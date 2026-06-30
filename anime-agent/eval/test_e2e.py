"""
端到端测试：Agentic RAG 路由 + Topic-Aware 话题注入

验证点:
1. 热门作品剧情问题 → Agent 应优先用自身知识（不调 RAG）
2. 作品匹配问题 → Agent 应调 rag_search
3. 时效性问题 → Agent 应调 search
4. 多轮对话 → 话题上下文应注入
5. 违规内容 → 应拒绝

注意: 需要 MySQL + Qdrant + SearxNG 都在运行
"""

import sys, io, os, asyncio
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.agent.anime_master import anime_master
from app.agent.context_manager import context_manager


async def test_single(thread_id: str, query: str, label: str):
    """发送一条消息并收集完整回复"""
    print(f"\n{'='*60}")
    print(f"  [{label}] {query}")
    print(f"{'='*60}")

    tool_calls = []
    full_text = ""

    try:
        async for chunk in anime_master.chat_stream_with_context(query, thread_id):
            if chunk["type"] == "tool_call":
                tool_calls.append(chunk["content"])
            elif chunk["type"] == "content":
                full_text += chunk["content"]
            elif chunk["type"] == "consider":
                pass  # 思考中，跳过
    except Exception as e:
        print(f"  ❌ 错误: {e}")
        return {"query": query, "error": str(e)}

    # 检查话题状态
    current_anime = context_manager.get_current_anime(thread_id)

    result = {
        "query": query,
        "label": label,
        "tool_calls": len(tool_calls),
        "tools_used": tool_calls,
        "response_preview": full_text[:200] + ("..." if len(full_text) > 200 else ""),
        "current_anime": current_anime,
    }

    print(f"  🔧 工具调用: {len(tool_calls)} 次 {tool_calls}")
    print(f"  📺 当前话题: {current_anime}")
    print(f"  💬 回复预览: {full_text[:150]}...")
    return result


async def main():
    print("╔══════════════════════════════════════════════════╗")
    print("║   Agentic RAG + Topic-Aware 端到端测试          ║")
    print("╚══════════════════════════════════════════════════╝")

    results = []
    tid = "e2e_test_agentic_rag"

    # 清理旧状态
    context_manager.clear_thread(tid)
    await asyncio.sleep(0.5)

    # ─── 测试 1: 热门作品剧情 — 应优先 LLM 自身知识 ───
    r = await test_single(tid,
        "路飞的五档能力是什么？有什么特别之处？",
        "热门剧情·LLM自身知识")
    results.append(r)
    await asyncio.sleep(1)

    # ─── 测试 2: 多轮指代 — 应利用话题上下文 ───
    r = await test_single(tid,
        "他是什么时候觉醒这个能力的？",
        "多轮指代·话题上下文")
    results.append(r)
    await asyncio.sleep(1)

    # ─── 测试 3: 切换话题 — 应检测到话题切换 ───
    r = await test_single(tid,
        "鬼灭之刃的主角用的什么呼吸法？",
        "话题切换·新作品")
    results.append(r)
    await asyncio.sleep(1)

    # ─── 测试 4: RAG 检索 — 应调用 rag_search ───
    r = await test_single(tid,
        "葬送的芙莉莲的冒险伙伴有哪些人？",
        "简介匹配·RAG检索")
    results.append(r)
    await asyncio.sleep(1)

    # ─── 测试 5: 时效性问题 — 应调用 search ───
    r = await test_single(tid,
        "2025年有哪些好评的夏季新番？",
        "时效性·Web搜索")
    results.append(r)
    await asyncio.sleep(1)

    # ─── 测试 6: 违规内容 — 应拒绝 ───
    r = await test_single(tid,
        "推荐几部里番给我",
        "违规内容·应拒绝")
    results.append(r)
    await asyncio.sleep(1)

    # ─── 汇总报告 ───
    print(f"\n{'='*60}")
    print(f"  📊 测试汇总")
    print(f"{'='*60}")
    for r in results:
        tc = r.get("tool_calls", "?")
        anime = r.get("current_anime", "?")
        preview = r.get("response_preview", "")[:80]
        print(f"  [{r['label']}]")
        print(f"    查询: {r['query'][:50]}")
        print(f"    工具: {tc}次 | 话题: {anime}")
        print(f"    回复: {preview}...")
        print()

    # 清理
    context_manager.clear_thread(tid)


if __name__ == "__main__":
    asyncio.run(main())