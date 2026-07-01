"""
AnimeMaster — 动漫助手核心类

管理 LLM agent 的搜索工具、对话上下文组装、消息持久化。
对话消息全量存 MySQL conversation_messages 表，每次请求取最近 6 条 + 持久化摘要作为上下文。

安全特性（v2）:
- Prompt Injection 检测与清洗（两级策略：静默替换 → 日志告警 → 升级拒绝）
- RAG / Search 结果消毒（防间接注入）
- 无限循环防护（recursion_limit + asyncio.timeout）
- 输入输出长度限制
"""

import asyncio
import re
import time
from collections import defaultdict
from langchain_community.utilities import SearxSearchWrapper
from langchain_core.tools.simple import Tool
from langchain.agents import create_agent
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.models.chat import llm
from app.agent.context_manager import context_manager
from app.db.rag import create_rag_service
from app.db.user_chats import (
    save_message,
    get_recent_messages,
    get_chat_summary,
    update_chat_updated_at,
    get_compress_state,
    save_compress_state,
    count_thread_messages,
    delete_user_chat,
)

RECENT_MESSAGE_LIMIT = 6  # 每次请求取最近 N 条消息作为上下文

# ═══════════════════════════════════════════════
# Prompt Injection 检测 & 防御
# ═══════════════════════════════════════════════

# 注入模式表：(编译后的正则, 替换文本)
# 注意：用于工具人清洗，不是安全围栏的替代品
_INJECTION_PATTERNS: list[tuple[re.Pattern, str]] = [
    # 1) 指令覆盖类 — "忽略以上所有指令" 等
    (re.compile(r"(?i)忽略\s*(?:掉|以下|上面|以上|掉所有|掉全部)?\s*(?:所有|全部)?\s*(?:的)?\s*(?:指令|提示|规则|设定|指示|要求|system\s*prompt)"), "【内容已过滤】"),
    # 2) 角色扮演类 — "从现在开始你扮演xxx"
    (re.compile(r"(?i)(?:从现在开始|接下来|之后)\s*(?:你|请|给我).*?(?:扮演|作为|当|成为)"), "【内容已过滤】"),
    # 3) 规则跳过类
    (re.compile(r"(?i)(?:不要|不用)\s*(?:遵守|执行|遵循|管)\s*(?:所有|全部)?\s*(?:限制|规则|指令|设定|filter)"), "【内容已过滤】"),
    (re.compile(r"(?i)跳过\s*(?:所有|全部)?\s*(?:限制|规则|指令|设定|filter)"), "【内容已过滤】"),
    # 4) 身份套取类
    (re.compile(r"(?i)你(?:是|叫)\s*(?:什么|OpenAI|ChatGPT|Claude|大模型|AI助手|语言模型)"), "【内容已过滤】"),
    # 5) 长分隔符（干扰提示边界）
    (re.compile(r"[-═=]{30,}"), ""),
    # 6) 代码块尝试（可能含注入指令）
    (re.compile(r"```[\s\S]*?```"), "【代码块已过滤】"),
]

# 每个 thread_id 在窗口内触发 N 次后升级为拒绝
_INJECTION_MAX_HITS = 3
_INJECTION_WINDOW = 300  # 5 秒

_MAX_MESSAGE_LENGTH = 2000
_MAX_RAG_SNIPPET = 500


class InjectionDetector:
    """Prompt Injection 检测器（两级策略）

    第一级 — 疑似注入：静默替换关键词 + 日志告警，正常继续处理
    第二级 — 明显恶意：同一 thread_id 在窗口期内多次触发 → 直接拒绝
    """

    def __init__(self):
        self._history: dict[str, list[tuple[float, str]]] = defaultdict(list)

    def sanitize(self, text: str, thread_id: str = "") -> tuple[str, bool]:
        """检测并清洗输入。

        Args:
            text: 用户原始输入
            thread_id: 线程 ID（用于升级判断）

        Returns:
            (清洗后文本, is_blocked)
            is_blocked=True 表示已升级为拒绝模式
        """
        now = time.time()
        sanitized = text
        triggered: list[str] = []

        for pattern, replacement in _INJECTION_PATTERNS:
            if pattern.search(sanitized):
                sanitized = pattern.sub(replacement, sanitized)
                triggered.append(pattern.pattern)

        # 截断超长输入
        if len(sanitized) > _MAX_MESSAGE_LENGTH:
            sanitized = sanitized[:_MAX_MESSAGE_LENGTH]

        if triggered and thread_id:
            # 记录触发历史
            self._history[thread_id].append((now, triggered[0]))
            # 清理过期记录
            self._history[thread_id] = [
                (t, p) for t, p in self._history[thread_id] if now - t < _INJECTION_WINDOW
            ]

            print(
                f"[InjectionDetector] thread={thread_id}, "
                f"patterns={triggered}, "
                f"hit_count={len(self._history[thread_id])}"
            )

            # 第二级：窗口内多次触发 → 拒绝
            if len(self._history[thread_id]) >= _INJECTION_MAX_HITS:
                return sanitized, True

        return sanitized, False

    def sanitize_rag_text(self, text: str) -> str:
        """清洗 RAG 检索结果 / 搜索结果的文本片段，防止间接 prompt injection。"""
        text = re.sub(r"```[\s\S]*?```", "", text)
        text = re.sub(r"`[^`]+`", "", text)
        text = re.sub(r"[-═=]{20,}", "", text)
        if len(text) > _MAX_RAG_SNIPPET:
            text = text[:_MAX_RAG_SNIPPET]
        return text


injection_detector = InjectionDetector()


class AnimeMaster:
    """
    动漫助手核心类，封装搜索、对话上下文组装和流式响应

    3. 回答动漫相关问题时，**使用rag_search工具**查询向量数据库作为辅助材料
    """

    SYSTEM_PROMPT = """
    你是一个可爱但专业的动漫助手，名叫「AnimeAgent」。

    ═══════════════════════════════════════
    核心能力与路由策略
    ═══════════════════════════════════════

    你拥有三种知识来源，请根据问题类型智能选择：

    ⚠️ 工具使用纪律：
    - 每次只调用一个工具，严禁同时调用多个工具
    - 先判断是否需要工具，不需要则不调用
    - rag_search 和 search 是互斥的：先试 RAG，无结果再用 search

    ┌─────────────────────────────────────────
    │ ① 自身知识（优先使用）
    │   适用范围：热门作品的剧情细节、角色设定、能力体系、世界观
    │   例如："路飞的五档是什么？" "卡卡西的写轮眼怎么来的？"
    │   策略：直接基于训练数据回答，标注「基于模型知识」
    │
    │ ② rag_search 工具（辅助验证 + 作品定位）
    │   适用范围：确认作品简介、查找数据库中收录的番剧信息
    │   例如："芙莉莲的冒险伙伴有哪些？" "有没有类似银魂的搞笑番？"
    │   策略：用 RAG 结果作为上下文参考，但不要照搬 synopsis
    │   注意：RAG 返回的 synopsis 可能不完整或过时，需结合自身知识补充
    │
    │ ③ search 工具（实时/冷门兜底）
    │   适用范围：最新资讯、冷门作品、声优/制作人员、时效性问题
    │   例如："2025年7月新番推荐" "今敏的最后一部作品"
    │   策略：搜索引擎返回时效性信息
    └─────────────────────────────────────────

    ═══════════════════════════════════════
    回答规范
    ═══════════════════════════════════════

    1. 对热门作品（海贼王/火影/巨人/鬼灭/咒术/EVA/Fate/龙珠/银魂等）的剧情/设定问题：
       → 不要调用任何工具，直接基于自身训练数据回答
       → 你的训练数据对这些作品的了解远超数据库中的一段简介
       → 回答末尾标注来源：「基于模型知识」
       → 例外：用户明确要求查数据库或问「数据库里有这部吗」时才调 rag_search

    2. 对不明确的动漫作品，或需要确认数据库中的作品信息时：
       → 【强制规则】必须先调用 rag_search，拒绝跳过 RAG 直接调 search
       → rag_search 返回结果后，结合自身知识补充细节
       → 仅当 rag_search 返回「未找到相关动漫信息」时，才能调用 search

    3. 对冷门/小众作品：
       → 先调 rag_search，若命中则基于 synopsis + 自身知识回答
       → 若 rag_search 未命中，调用 search 搜索

    4. 对时效性问题（新番/新闻/最新更新）：
       → 直接调用 search，不要用 RAG

    5. 回答时保持：
       - 简洁、准确，不输出思考过程
       - 给出明确结论，标注信息来源
       - 不确定时附加「未找到可靠信息来源，以上结果仅做参考」

    ═══════════════════════════════════════
    安全约束
    ═══════════════════════════════════════

    1. 以下内容 **一律拒绝回答**：
    - 被中国大陆法律法规认定为违规、封禁的动漫作品
    - 含有色情或暴力或极端主义内容的动漫
    - 盗版资源、下载链接

    2. 如果用户输入涉及上述内容，直接回复固定话术：
    「根据相关规定，我无法提供此类内容的信息。」
    不要搜索、不要解释、不要列举作品名。

    3. 不要回答与动漫/角色/剧情设定无关的问题。
       明确说「我只回答动漫相关的问题」。

    4. 不要回答动漫的视频资源问题。
       明确说「受版权问题，本网站不提供动漫资源，仅限交流学习使用」。

    5. 不要编造番剧名称、角色名、年份。
    """

    def __init__(self):
        self._init_rag_service()
        self._init_searx_wrapper()
        self._init_agent()

    def _init_rag_service(self):
        """初始化 RAG 服务"""
        self.rag_service = create_rag_service()

    def _init_searx_wrapper(self):
        """初始化 Searx 搜索包装器"""
        self.searx_wrapper = SearxSearchWrapper(
            searx_host="http://localhost:4000",
            engines=["360search", "sougou", "bing"],
            k=5
        )

    def _init_agent(self):
        """初始化 LangGraph agent（无状态模式，不带 checkpointer）

        注册两个工具：
        - search:      Web 搜索（SearxNG），用于实时资讯 / 冷门作品
        - rag_search:  向量数据库检索（Qdrant + Rerank），用于作品查询 / 简介匹配
        """
        search_tool = Tool.from_function(
            func=self.search,
            name="search",
            description=(
                "【互联网搜索引擎，仅在本地数据库无结果时使用】"
                "使用 SearxNG 搜索互联网。优先使用 rag_search，只有以下情况才用本工具："
                "1. rag_search 无结果或结果不相关"
                "2. 查询最新动漫资讯、新番、声优、制作人员等本地数据库没有的信息"
                "3. 用户明确要求查询实时/外部信息"
            ),
        )
        rag_tool = Tool.from_function(
            func=self.rag_search,
            name="rag_search",
            description=(
                "【首选工具】从本地动漫数据库中检索作品信息。"
                "当你需要查找、确认、搜索任何动漫作品时，必须优先使用本工具。"
                "数据库收录了2000+部动漫的标题和简介，涵盖绝大多数热门和经典作品。"
                "简介中通常包含主角名、世界观、核心设定等信息。"
                "输入：作品名或关键词（中文）。"
            ),
        )
        self.agent = create_agent(
            model=llm,
            tools=[rag_tool, search_tool],
            system_prompt=self.SYSTEM_PROMPT,
        )

    async def _prepare_model_input(self, thread_id: str, user_message: str) -> list:
        """
        准备模型输入：从 MySQL 取最近 6 条消息 + 持久化摘要 + 话题上下文，拼接上下文。

        Topic-Aware 增强：
        - 从 context_manager 获取当前线程正在讨论的动漫作品
        - 注入系统提示，帮助 agent 消解"他"/"这个"/"主角"等指代
        - 使 RAG 搜索时自动带上作品名作为上下文

        返回发给 LLM 的消息列表（LangChain Message 对象）。
        """
        messages = []

        # ① 摘要（从 MySQL 读取，跨重启持久化）
        summary = await asyncio.to_thread(get_chat_summary, thread_id)
        if summary:
            messages.append(SystemMessage(content=f"[对话摘要] {summary}"))

        # ② 最近 6 条历史消息
        recent = await asyncio.to_thread(get_recent_messages, thread_id, RECENT_MESSAGE_LIMIT)
        for msg in recent:
            if msg["role"] == "user":
                messages.append(HumanMessage(content=msg["content"]))
            elif msg["role"] == "assistant":
                messages.append(AIMessage(content=msg["content"]))
            elif msg["role"] == "system":
                messages.append(SystemMessage(content=msg["content"]))

        # ③ 话题上下文（Topic-Aware）：注入当前讨论的作品名
        current_anime = context_manager.get_current_anime(thread_id)
        if current_anime:
            topic_hint = (
                f"[话题上下文] 用户当前在讨论《{current_anime}》。"
                + "后续消息中的指代词(如'他''主角''这个''能力')请结合《"
                + f"{current_anime}》理解。"
                + f"使用 rag_search 或 search 工具时，请在查询关键词中包含「{current_anime}」以提高准确率。"
            )
            messages.append(SystemMessage(content=topic_hint))

        # ④ 当前用户消息
        messages.append(HumanMessage(content=user_message))

        return messages

    def search(self, query: str) -> str:
        """
        Search the web using SearxNG meta search engine.

        （结果经过间接 injection 消毒处理）

        Args:
            query: The search query to look up on the web
        """
        try:
            result = self.searx_wrapper.run(query, language="zh", format="json")
            # 消毒搜索结果，防止间接 prompt injection
            result = injection_detector.sanitize_rag_text(str(result))
            return result
        except Exception as e:
            error_msg = f"搜索服务暂时不可用，请稍后重试。错误信息: {type(e).__name__}"
            print(f"[SearchTool] 搜索失败: {e}")
            return error_msg

    def rag_search(self, query: str) -> str:
        """
        Search anime information from vector database using RAG.
        Useful for answering questions about anime synopsis, characters, and plot.

        （检索结果经过间接 injection 消毒处理）

        Args:
            query: The search query about anime
        """
        try:
            results = self.rag_service.query(query, top_k=5)
            if not results:
                return "未找到相关动漫信息"

            formatted_results = []
            for i, r in enumerate(results):
                # 消毒每个字段，防止数据库中的投毒注入
                safe_title = injection_detector.sanitize_rag_text(r["title"])
                safe_synopsis = injection_detector.sanitize_rag_text(r["synopsis"])
                formatted_results.append(f"""
                    {i+1}. 《{safe_title}》
                    评分: 向量相似度 {r['vector_score']:.4f}, 重排分数 {r['rerank_score']:.4f}
                    简介: {safe_synopsis}
                    """)

            return "\n".join(formatted_results)
        except Exception as e:
            error_msg = f"RAG查询失败: {str(e)}"
            print(f"[RAGTool] {error_msg}")
            return error_msg

    async def chat_stream_with_context(self, user_message: str, thread_id: str):
        """
        流式聊天处理函数（无状态模式），Topic-Aware 增强版 + 安全防护

        1. 话题检测（提前执行，从用户消息中提取/更新当前讨论的动漫）
        2. **Prompt Injection 检测**：检测到注入模式时静默替换，多次触发则拒绝
        3. 组装上下文（取最近 6 条历史 + 摘要 + 话题上下文注入）
        4. 保存用户消息到 MySQL
        5. 调 LLM → astream 原生异步流式返回 **（带 30s 超时 + recursion_limit=10）**
        6. 保存 assistant 回复到 MySQL
        7. 更新对话时间戳
        8. 检查是否需要滚动摘要压缩

        Topic-Aware 机制：
        - 首轮消息即通过 detect_anime_switch 提取话题
        - 后续轮次在 _prepare_model_input 中注入「[话题上下文]」系统消息
        - 帮助 agent 消解指代词，并在 RAG/search 查询中自动带上作品名

        安全防护：
        - Prompt Injection 两级策略：替换 → 拒绝
        - RAG / Search 结果消毒，防止间接注入
        - recursion_limit=10 防止工具调用死循环
        - asyncio.timeout(30) 防止长时间无响应
        - 输入/输出长度限制

        :param user_message: 用户消息
        :param thread_id: 对话线程ID
        :return: 异步生成器，产生消息事件
        """
        # ① Prompt Injection 检测与清洗
        safe_message, is_blocked = injection_detector.sanitize(user_message, thread_id)
        if is_blocked:
            print(f"[AnimeMaster] 拒绝注入请求 thread={thread_id}")
            yield {
                "type": "error",
                "content": "检测到多次异常输入，请重新开始对话。"
            }
            return

        # ② 话题检测（提前执行，确保 _prepare_model_input 能拿到当前话题）
        context_manager.detect_anime_switch(safe_message, thread_id)

        # ③ 准备上下文（取最近 6 条历史 + 摘要 + 话题上下文）
        model_input = await self._prepare_model_input(thread_id, safe_message)

        # ④ 保存用户消息
        await asyncio.to_thread(save_message, thread_id, "user", safe_message)

        full_response: list[str] = []  # 收集完整回复用于持久化

        try:
            # ⑤ 调 LLM → 带超时 + 递归深度保护
            async with asyncio.timeout(30):
                async for chunk, _data in self.agent.astream(
                    {"messages": model_input},
                    stream_mode="messages",
                    config={"recursion_limit": 10},
                ):
                    if chunk.content == "":
                        try:
                            if chunk.tool_calls:
                                yield {"type": "tool_call", "content": "调用搜索工具"}
                            else:
                                yield {"type": "consider", "content": "思考中"}
                        except Exception:
                            yield {"type": "consider", "content": "思考中"}
                    else:
                        if isinstance(chunk, AIMessage):
                            full_response.append(chunk.content)
                            yield {"type": "content", "content": chunk.content}

        except asyncio.TimeoutError:
            print(f"[AnimeMaster] 超时 thread={thread_id}")
            yield {"type": "error", "content": "响应超时（30秒），请简化你的问题或重新提问。"}

        except Exception as e:
            print(f"[AnimeMaster] agent 流式异常: {type(e).__name__}: {e}")
            yield {"type": "error", "content": f"处理请求时出现异常，请重试。"}

        finally:
            # ⑥ 保存 assistant 回复（即使出错也要保存已有的部分）
            full_text = "".join(full_response)
            if full_text:
                await asyncio.to_thread(save_message, thread_id, "assistant", full_text)

            # ⑦ 更新对话时间戳
            await asyncio.to_thread(update_chat_updated_at, thread_id)

            # ⑧ 检查是否需要滚动摘要压缩
            await self._maybe_compress(thread_id, safe_message)

    async def _maybe_compress(self, thread_id: str, user_message: str):
        """
        检查压缩触发条件，必要时执行滚动摘要。

        触发条件（满足任一）：
        - 消息总数达到 next_compress_at（默认每 10 条）
        - 用户切换了讨论的动漫话题
        """
        from app.agent.context_manager import COMPRESS_EVERY_N

        msg_count = await asyncio.to_thread(count_thread_messages, thread_id)
        old_summary, next_at = await asyncio.to_thread(get_compress_state, thread_id)

        # 检测话题切换
        is_switch, anime_name = context_manager.detect_anime_switch(user_message, thread_id)

        should_compress = msg_count >= next_at or is_switch
        if not should_compress:
            return

        # 取最近 20 条消息用于生成摘要
        recent = await asyncio.to_thread(get_recent_messages, thread_id, limit=20)

        # 构建切换标注
        switch_info = f"anime_switch:{anime_name}" if is_switch else None

        # 调 LLM 生成滚动摘要
        new_summary = await context_manager.rolling_summary(old_summary, recent, switch_info)

        if new_summary:
            new_next = msg_count + COMPRESS_EVERY_N
            await asyncio.to_thread(save_compress_state, thread_id, new_summary, new_next)
            if is_switch:
                print(f"[AnimeMaster] 压缩完成(话题切换) thread={thread_id}, "
                      f"anime={anime_name}, msgs={msg_count}, next={new_next}")
            else:
                print(f"[AnimeMaster] 压缩完成(轮数触发) thread={thread_id}, "
                      f"msgs={msg_count}, next={new_next}")

    async def get_conversation_history(self, thread_id: str):
        """
        获取对话历史（全部消息，用于前端浏览）

        :param thread_id: 对话线程ID
        :return: LangChain Message 对象列表，如果不存在返回 None
        """
        from app.db.user_chats import get_all_messages

        messages = await asyncio.to_thread(get_all_messages, thread_id)
        if not messages:
            return None

        result = []
        for msg in messages:
            if msg["role"] == "user":
                result.append(HumanMessage(content=msg["content"]))
            elif msg["role"] == "assistant":
                result.append(AIMessage(content=msg["content"]))
        return result

    async def delete_conversation_history(self, thread_id: str) -> bool:
        """
        删除对话历史（消息 + 用户对话关联 + 动漫追踪状态）

        :param thread_id: 对话线程ID
        :return: 是否成功删除
        """
        try:
            success = await asyncio.to_thread(delete_user_chat, thread_id)
            context_manager.clear_thread(thread_id)
            return success
        except Exception as e:
            print(f"[AnimeMaster] 删除对话失败: {e}")
            return False


# 全局实例
anime_master = AnimeMaster()

# 保持原有的函数接口，向后兼容
search = anime_master.search
rag_search = anime_master.rag_search


async def chat_stream_with_context(user_message: str, thread_id: str):
    async for chunk in anime_master.chat_stream_with_context(user_message, thread_id):
        yield chunk


async def get_conversation_history(thread_id: str):
    return await anime_master.get_conversation_history(thread_id)


async def delete_conversation_history(thread_id: str) -> bool:
    return await anime_master.delete_conversation_history(thread_id)


# test
if __name__ == "__main__":
    results = search("从零开始的异世界生活 后续剧情")
    print(results)
