import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 开启离线模式，避免从网络下载模型
os.environ['TRANSFORMERS_OFFLINE'] = '1'
os.environ['SENTENCE_TRANSFORMERS_HOME'] = './models'

from typing import List, Dict
from sentence_transformers import SentenceTransformer, CrossEncoder

from app.db.build_qdrant_vector_db import COLLECTION_NAME
from app.db.qdrant_client import get_qdrant_client

# Rerank 模型
RERANK_MODEL_NAME = "BAAI/bge-reranker-v2-m3"

# 本地模型路径
LOCAL_EMBEDDING_MODEL_PATH = "C:\\Users\\20453\\.cache\\huggingface\\hub\\models--BAAI--bge-large-zh\\snapshots"
LOCAL_RERANK_MODEL_PATH = "C:\\Users\\20453\\.cache\\huggingface\\hub\\models--BAAI--bge-reranker-v2-m3\\snapshots"


class AnimeRAG:
    _instance = None
    _initialized = False

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(AnimeRAG, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if self._initialized:
            return

        self.embedding_model = SentenceTransformer(LOCAL_EMBEDDING_MODEL_PATH)
        self.rerank_model = CrossEncoder(LOCAL_RERANK_MODEL_PATH)
        self.qdrant_client = get_qdrant_client()
        self._keyword_index = None  # 懒加载：{anime_id: {"title": ..., "synopsis": ...}}

        self._initialized = True
    
    def embed_text(self, text: str) -> List[float]:
        """文本向量化"""
        embedding = self.embedding_model.encode(text)
        return embedding.tolist()
    
    def _search_vector(self, query: str, top_k: int = 20) -> List[Dict]:
        """向量检索（初步检索较多结果用于rerank）"""
        query_embedding = self.embed_text(query)
        results = self.qdrant_client.query_points(
            collection_name=COLLECTION_NAME,
            query=query_embedding,
            limit=top_k
        )
        points = results.points if hasattr(results, 'points') else results
        
        return [
            {
                "anime_id": point.payload["anime_id"],
                "title": point.payload["title"],
                "synopsis": point.payload["synopsis"],
                "vector_score": point.score
            }
            for point in points
        ]
    
    # ═══════════════════════════════════════════════════
    # 关键词索引（客户端侧，补偿 Qdrant 本地模式无全文索引）
    # ═══════════════════════════════════════════════════

    def _build_keyword_index(self):
        """构建内存关键词索引：title → anime_id 映射"""
        if self._keyword_index is not None:
            return

        print("[HybridSearch] 构建关键词索引...")
        from qdrant_client.models import Filter, FieldCondition, MatchAny

        # 从 Qdrant 读取所有 anime_id
        all_points, next_offset = self.qdrant_client.scroll(
            collection_name=COLLECTION_NAME,
            limit=100,
            with_payload=True,
            with_vectors=False,
        )
        while next_offset:
            batch, next_offset = self.qdrant_client.scroll(
                collection_name=COLLECTION_NAME,
                limit=100,
                offset=next_offset,
                with_payload=True,
                with_vectors=False,
            )
            all_points.extend(batch)

        # 建索引：{anime_id: {title, synopsis, point_id}}
        self._keyword_index = {}
        for point in all_points:
            aid = point.payload["anime_id"]
            if aid not in self._keyword_index:
                self._keyword_index[aid] = {
                    "title": point.payload.get("title", ""),
                    "synopsis": point.payload.get("synopsis", ""),
                    "point_id": point.id,
                    "vector_score": 0.0,
                    "rerank_score": 0.0,
                }

        print(f"[HybridSearch] 索引就绪: {len(self._keyword_index)} 部动漫")

    def _search_keyword(self, query: str, top_k: int = 20) -> List[Dict]:
        """客户端侧关键词检索：基于 title 字段的 TF 匹配 + 子串精确命中"""
        self._build_keyword_index()

        scores = {}  # {anime_id: score}

        # ── 策略 1：精确子串匹配（"海贼王" in title）─ 最高权重 ──
        query_lower = query.lower()
        for aid, info in self._keyword_index.items():
            title = info["title"].lower()
            if query_lower in title or title in query_lower:
                scores[aid] = scores.get(aid, 0) + 2.0

        # ── 策略 2：分词匹配 ─ 按命中 token 数计分 ──
        query_tokens = set(query_lower.replace(" ", ""))
        for aid, info in self._keyword_index.items():
            title = info["title"].lower().replace(" ", "")
            # Jaccard-like: 交集 / 查询长度
            title_tokens = set(title)
            overlap = len(query_tokens & title_tokens)
            if overlap > 0:
                # 额外加分（子串匹配已经给了 2.0，这里给符号分）
                jaccard = overlap / max(len(query_tokens), 1)
                scores[aid] = scores.get(aid, 0) + jaccard

        # ── 排序 ──
        ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)
        results = []
        for aid, score in ranked[:top_k]:
            info = self._keyword_index[aid]
            results.append({
                "anime_id": aid,
                "title": info["title"],
                "synopsis": info["synopsis"],
                "vector_score": score,  # 复用字段表示关键词分数
            })

        return results

    def _rrf_fusion(
        self,
        vector_results: List[Dict],
        keyword_results: List[Dict],
        k: int = 60,
    ) -> List[Dict]:
        """Reciprocal Rank Fusion：合并向量和关键词两路结果"""
        if not keyword_results:
            return vector_results
        if not vector_results:
            return keyword_results

        # 构建 RRF 分数映射
        rrf_scores = {}

        # 向量路
        for rank, item in enumerate(vector_results):
            aid = item["anime_id"]
            rrf_scores[aid] = {
                "anime_id": aid,
                "title": item["title"],
                "synopsis": item["synopsis"],
                "vector_score": item.get("vector_score", 0.0),
                "rrf_score": 1.0 / (k + rank + 1),
            }

        # 关键词路
        for rank, item in enumerate(keyword_results):
            aid = item["anime_id"]
            kw_rrf = 1.0 / (k + rank + 1)
            if aid in rrf_scores:
                rrf_scores[aid]["rrf_score"] += kw_rrf
            else:
                rrf_scores[aid] = {
                    "anime_id": aid,
                    "title": item["title"],
                    "synopsis": item["synopsis"],
                    "vector_score": item.get("vector_score", 0.0),
                    "rrf_score": kw_rrf,
                }

        # 按 RRF 分数排序
        fused = sorted(rrf_scores.values(), key=lambda x: x["rrf_score"], reverse=True)
        return fused

    # ═══════════════════════════════════════════════════
    # 查询入口
    # ═══════════════════════════════════════════════════

    def query(self, query: str, top_k: int = 5, vector_top_k: int = 20) -> List[Dict]:
        """混合检索：向量 + 关键词 → RRF 融合 → Rerank"""
        # 两路并行召回
        vector_candidates = self._search_vector(query, top_k=vector_top_k)
        keyword_candidates = self._search_keyword(query, top_k=vector_top_k)

        # RRF 融合
        fused = self._rrf_fusion(vector_candidates, keyword_candidates)
        fused = fused[:vector_top_k]  # 截断，交由 rerank 精选

        # Rerank
        results = self._rerank(query, fused, top_k=top_k)
        return results

    def _rerank(self, query: str, candidates: List[Dict], top_k: int = 5) -> List[Dict]:
        """重排：使用 CrossEncoder 对向量检索结果二次排序"""
        if not candidates:
            return []
        
        pairs = [
            (query, f"{c['title']} {c['synopsis']}") 
            for c in candidates
        ]
        
        scores = self.rerank_model.predict(pairs)
        
        for candidate, score in zip(candidates, scores):
            candidate["rerank_score"] = float(score)
        
        candidates_sorted = sorted(
            candidates, 
            key=lambda x: x["rerank_score"], 
            reverse=True
        )
        
        return candidates_sorted[:top_k]


def create_rag_service() -> AnimeRAG:
    """创建 RAG 服务实例（单例）"""
    return AnimeRAG()


if __name__ == "__main__":
    print("=== Testing Hybrid RAG (Vector + Keyword → RRF → Rerank) ===\n")

    rag = create_rag_service()

    test_queries = [
        ("精确标题", "银魂"),
        ("英文别名", "Naruto"),
        ("语义描述", "高中生获得能杀死任何人的笔记本"),
        ("续作区分", "进击的巨人第三季"),
        ("冷门外传", "海贼王 萨博的故事"),
    ]

    try:
        for label, query in test_queries:
            print(f"[{label}] Query: {query}")
            results = rag.query(query, top_k=5)
            for i, r in enumerate(results):
                kw = r.get("vector_score", 0)
                rr = r.get("rerank_score", 0)
                print(f"  {i+1}. [kw={kw:.2f}, rerank={rr:.4f}] {r['title']} (id={r['anime_id']})")
            print()
    finally:
        rag = None
        import gc
        gc.collect()
    print("=== Test Complete ===")
