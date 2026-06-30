"""
RAG 评测引擎

对向量检索和 Rerank 两个阶段分别评测，输出分阶段、分类别的指标报告。
只测检索质量（Hit Rate / MRR / NDCG），不做 LLM 生成评测。

用法:
    cd anime-agent
    python eval/eval_rag.py                    # 全量评测
    python eval/eval_rag.py --category alias   # 只测别名类
    python eval/eval_rag.py --stage vector     # 只看向量检索，不跑 rerank
"""

import sys, io, os, json, time, argparse
from collections import defaultdict
from typing import List, Dict, Optional, Tuple

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.db.rag import create_rag_service


# ═══════════════════════════════════════════════════
# 加载评测集
# ═══════════════════════════════════════════════════

def load_eval_dataset(path: str = None) -> dict:
    if path is None:
        path = os.path.join(os.path.dirname(__file__), "eval_dataset.json")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


# ═══════════════════════════════════════════════════
# 指标计算
# ═══════════════════════════════════════════════════

def hit_rate(results: List[Dict], expected_ids: List[int], k: int) -> bool:
    """@k 命中率：前 k 个结果中是否包含任一期望 anime_id"""
    retrieved_ids = {r["anime_id"] for r in results[:k]}
    return any(eid in retrieved_ids for eid in expected_ids)


def reciprocal_rank(results: List[Dict], expected_ids: List[int]) -> float:
    """倒数排名：第一个命中结果排第几，未命中返回 0"""
    for i, r in enumerate(results):
        if r["anime_id"] in expected_ids:
            return 1.0 / (i + 1)
    return 0.0


def ndcg_at_k(results: List[Dict], expected_ids: List[int], k: int) -> float:
    """Normalized DCG@k：用 rerank_score 作为相关性分数"""
    import math

    # 构建理想排序：期望的 anime_id 得最高分，其他得 0
    max_score = max((r.get("rerank_score", r.get("vector_score", 0)) for r in results), default=1.0)
    ideal_relevance = [max_score if r["anime_id"] in expected_ids else 0.0 for r in results[:k]]
    actual_relevance = [r.get("rerank_score", r.get("vector_score", 0)) if r["anime_id"] in expected_ids else 0.0 for r in results[:k]]

    def dcg(rel):
        return sum(rel[i] / math.log2(i + 2) for i in range(len(rel)))

    idcg = dcg(sorted(ideal_relevance, reverse=True))
    if idcg == 0:
        return 0.0
    return dcg(actual_relevance) / idcg


# ═══════════════════════════════════════════════════
# 评测主流程
# ═══════════════════════════════════════════════════

def evaluate_retrieval(
    queries: List[dict],
    categories: dict,
    stage: str = "rerank",  # "vector" | "rerank"
    vector_top_k: int = 20,
    rerank_top_k: int = 5,
) -> dict:
    """
    对检索链路做离线评测。

    - stage="vector": 只看向量检索（不跑 rerank），top_k = vector_top_k
    - stage="rerank":  向量检索 → rerank → 看最终的 top_k

    只评测有 expected_anime_ids 的 query（跳过 negative + cold_start 等无期望 ID 的）。
    """
    rag = create_rag_service()
    print(f"RAG 服务初始化完成 | 评测阶段: {stage}")

    # 筛选有期望 ID 的 query
    eval_queries = [q for q in queries if q.get("expected_anime_ids")]

    # 按 category 分组
    by_category = defaultdict(list)
    for q in eval_queries:
        by_category[q["category"]].append(q)

    # 累积指标
    category_metrics = {}
    all_hits = {k: 0 for k in [1, 3, 5, 10]}
    all_rr = 0.0
    all_ndcg_5 = 0.0
    all_ndcg_10 = 0.0
    total = 0

    per_query_results = []

    for q in eval_queries:
        qid, query, expected_ids = q["id"], q["query"], q["expected_anime_ids"]
        cat = q["category"]

        # ── 检索 ──
        t0 = time.time()
        if stage == "vector":
            results = rag._search_vector(query, top_k=vector_top_k)
        else:
            results = rag.query(query, top_k=rerank_top_k, vector_top_k=vector_top_k)
        elapsed = time.time() - t0

        k = len(results)

        # ── 指标 ──
        hr1 = hit_rate(results, expected_ids, 1)
        hr3 = hit_rate(results, expected_ids, 3)
        hr5 = hit_rate(results, expected_ids, 5)
        hr10 = hit_rate(results, expected_ids, 10)
        rr = reciprocal_rank(results, expected_ids)
        ndcg5 = ndcg_at_k(results, expected_ids, 5)
        ndcg10 = ndcg_at_k(results, expected_ids, 10)

        all_hits[1] += hr1
        all_hits[3] += hr3
        all_hits[5] += hr5
        all_hits[10] += hr10
        all_rr += rr
        all_ndcg_5 += ndcg5
        all_ndcg_10 += ndcg10
        total += 1

        per_query_results.append({
            "id": qid,
            "category": cat,
            "query": query,
            "expected_title": q.get("expected_title"),
            "hit@1": hr1,
            "hit@3": hr3,
            "hit@5": hr5,
            "mrr": rr,
            "ndcg@5": round(ndcg5, 4),
            "top_result": results[0]["title"] if results else None,
            "top_result_id": results[0]["anime_id"] if results else None,
            "latency_ms": round(elapsed * 1000, 1),
        })

    # ── 按类别汇总 ──
    for cat, cat_queries in by_category.items():
        cat_ids = {q["id"] for q in cat_queries}
        cat_results = [r for r in per_query_results if r["id"] in cat_ids]
        n = len(cat_results)
        if n == 0:
            continue
        category_metrics[cat] = {
            "count": n,
            "label": categories.get(cat, cat),
            "hit@1": round(sum(r["hit@1"] for r in cat_results) / n, 4),
            "hit@3": round(sum(r["hit@3"] for r in cat_results) / n, 4),
            "hit@5": round(sum(r["hit@5"] for r in cat_results) / n, 4),
            "mrr": round(sum(r["mrr"] for r in cat_results) / n, 4),
            "ndcg@5": round(sum(r["ndcg@5"] for r in cat_results) / n, 4),
        }

    return {
        "meta": {
            "stage": stage,
            "total_queries": total,
            "vector_top_k": vector_top_k,
            "rerank_top_k": rerank_top_k,
        },
        "overall": {
            "hit@1": round(all_hits[1] / total, 4),
            "hit@3": round(all_hits[3] / total, 4),
            "hit@5": round(all_hits[5] / total, 4),
            "hit@10": round(all_hits[10] / total, 4) if stage == "vector" else None,
            "mrr": round(all_rr / total, 4),
            "ndcg@5": round(all_ndcg_5 / total, 4),
        },
        "by_category": category_metrics,
        "per_query": per_query_results,
    }


def print_report(report: dict):
    """打印人类可读的评测报告"""
    meta = report["meta"]
    overall = report["overall"]
    by_cat = report["by_category"]
    per_q = report["per_query"]

    print()
    print("=" * 70)
    print(f"  🎯 Anime RAG 评测报告")
    print(f"  阶段: {meta['stage']} | 向量初筛: {meta['vector_top_k']} | Rerank: {meta['rerank_top_k']} | 有效查询: {meta['total_queries']}")
    print("=" * 70)

    # ── 总览 ──
    print()
    print(f"  📊 总体指标")
    print(f"  {'─' * 40}")
    print(f"  Hit@1        {overall['hit@1']:.1%}")
    print(f"  Hit@3        {overall['hit@3']:.1%}")
    print(f"  Hit@5        {overall['hit@5']:.1%}")
    if overall.get("hit@10"):
        print(f"  Hit@10       {overall['hit@10']:.1%}")
    print(f"  MRR          {overall['mrr']:.4f}")
    print(f"  NDCG@5       {overall['ndcg@5']:.4f}")

    # ── 分类别 ──
    print()
    print(f"  📂 分类别指标")
    print(f"  {'─' * 60}")
    header = f"  {'类别':<16} {'数量':>4}  {'Hit@1':>7}  {'Hit@3':>7}  {'Hit@5':>7}  {'MRR':>7}  {'NDCG@5':>7}"
    print(header)
    print(f"  {'─' * 60}")

    cat_order = ["synopsis_match", "alias_variant", "detail_qa", "cold_start", "multi_hop", "negative"]
    for cat in cat_order:
        if cat in by_cat:
            m = by_cat[cat]
            label = m["label"]
            print(f"  {label:<16} {m['count']:>4}  {m['hit@1']:>7.1%}  {m['hit@3']:>7.1%}  {m['hit@5']:>7.1%}  {m['mrr']:>7.4f}  {m['ndcg@5']:>7.4f}")

    # ── 失败案例 ──
    failed = [r for r in per_q if not r["hit@1"]]
    if failed:
        print()
        print(f"  ⚠️  Hit@1 失败 ({len(failed)}/{len(per_q)})")
        print(f"  {'─' * 60}")
        for r in failed[:10]:
            top_info = f"→ {r['top_result']} (id={r['top_result_id']})" if r['top_result'] else "→ 无结果"
            print(f"  [{r['category']}] {r['query'][:45]:<45} {top_info}")
        if len(failed) > 10:
            print(f"  ... 还有 {len(failed) - 10} 条")

    # ── 不可评测类（cold_start / negative）说明 ──
    print()
    print(f"  ℹ️  cold_start / negative / multi_hop 类查询需要 agent 层评测，")
    print(f"     本脚本仅覆盖可验证期望 anime_id 的检索类查询。")

    print()
    print("=" * 70)


def save_report(report: dict, path: str = None):
    """保存 JSON 报告，文件名带时间戳，方便对比"""
    if path is None:
        timestamp = time.strftime("%Y%m%d_%H%M%S")
        eval_dir = os.path.dirname(__file__)
        report_dir = os.path.join(eval_dir, "reports")
        os.makedirs(report_dir, exist_ok=True)
        path = os.path.join(report_dir, f"eval_{timestamp}.json")

    with open(path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"📁 报告已保存: {path}")


# ═══════════════════════════════════════════════════
# CLI
# ═══════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="Anime RAG 评测引擎")
    parser.add_argument("--category", type=str, default=None,
                        help="只测指定类别 (synopsis_match/alias_variant/detail_qa/...)")
    parser.add_argument("--stage", type=str, default="rerank",
                        choices=["vector", "rerank"],
                        help="评测阶段: vector(向量初筛) 或 rerank(最终排序)")
    parser.add_argument("--vector-top-k", type=int, default=20)
    parser.add_argument("--rerank-top-k", type=int, default=5)
    parser.add_argument("--output", type=str, default=None,
                        help="报告输出路径")
    args = parser.parse_args()

    dataset = load_eval_dataset()
    all_queries = dataset["queries"]

    if args.category:
        all_queries = [q for q in all_queries if q["category"] == args.category]
        if not all_queries:
            print(f"❌ 未找到类别: {args.category}")
            print(f"   可用类别: {list(dataset['meta']['categories'].keys())}")
            return

    print(f"加载 {len(all_queries)} 条评测 query")
    print(f"类别: {args.category or '全部'} | 阶段: {args.stage}")

    report = evaluate_retrieval(
        all_queries,
        dataset["meta"]["categories"],
        stage=args.stage,
        vector_top_k=args.vector_top_k,
        rerank_top_k=args.rerank_top_k,
    )

    print_report(report)
    save_report(report, args.output)


if __name__ == "__main__":
    main()
