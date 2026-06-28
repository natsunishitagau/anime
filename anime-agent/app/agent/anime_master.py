"""
AnimeMaster — 动漫助手核心类

管理 LLM agent 的搜索工具、对话上下文组装、消息持久化。
对话消息全量存 MySQL conversation_messages 表，每次请求取最近 6 条 + 持久化摘要作为上下文。
"""

import asyncio
from langchain_community.utilities import SearxSearchWrapper
from langchain_core.tools.simple import Tool
from langchain.agents import create_agent
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.models.chat import llm
from app.agent.context_manager import context_manager
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


class AnimeMaster:
    """
    动漫助手核心类，封装搜索、对话上下文组装和流式响应

    3. 回答动漫相关问题时，**使用rag_search工具**查询向量数据库作为辅助材料
    """

    SYSTEM_PROMPT = """
    你是一个可爱但专业的动漫助手，名叫「AnimeAgent」。

    你的任务：
    1. 回答用户关于动漫、角色、剧情设定的问题
    2. 不明确用户问题时，**使用search工具**判断是否是相关问题
    3. 其他事实类问题，**使用search工具**搜索
    4. 回答时要：
    - 简洁、准确
    - 不输出思考过程
    - 给出明确结论
    - 标注信息来源

    约束：
    - 不要编造番剧名称、角色、年份
    - 不要回答与动漫、角色、剧情设定无关的问题，明确说「我只回答动漫相关的问题」
    - 不要回答动漫的视频资源问题，明确说「受版权问题，本网站不提供动漫资源，仅限交流学习使用」
    - 不确定时，仍需要思考回答一些内容，并最后附加「未找到可靠信息来源，以上结果仅做参考」

    注意：
    1. 以下内容 **一律拒绝回答**：
    - 被中国大陆法律法规认定为违规、封禁的动漫作品
    - 含有色情或暴力或极端主义内容的动漫

    2. 如果用户输入涉及上述内容：
    - 不要搜索
    - 不要解释原因
    - 不要列举作品名
    - 直接回复固定话术：

    「根据相关规定，我无法提供此类内容的信息。」

    3. 若搜索结果中出现上述作品：
    - 忽略该结果
    - 不要引用、不要总结

    4. 你的知识截止日期未知，因此 **事实性问题必须使用工具**。
    """

    def __init__(self):
        # self._init_rag_service()
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
        """初始化 LangGraph agent（无状态模式，不带 checkpointer）"""
        search_tool = Tool.from_function(
            func=self.search,
            name="search",
            description="Search the web using SearxNG meta search engine. Useful when you need to find current information or facts.",
        )
        self.agent = create_agent(
            model=llm,
            tools=[search_tool],
            system_prompt=self.SYSTEM_PROMPT,
        )

    async def _prepare_model_input(self, thread_id: str, user_message: str) -> list:
        """
        准备模型输入：从 MySQL 取最近 6 条消息 + 持久化摘要，拼接上下文。

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

        # ③ 当前用户消息
        messages.append(HumanMessage(content=user_message))

        return messages

    def search(self, query: str) -> str:
        """
        Search the web using SearxNG meta search engine.

        Args:
            query: The search query to look up on the web
        """
        try:
            result = self.searx_wrapper.run(query, language="zh", format="json")
            return result
        except Exception as e:
            error_msg = f"搜索服务暂时不可用，请稍后重试。错误信息: {type(e).__name__}"
            print(f"[SearchTool] 搜索失败: {e}")
            return error_msg

    def rag_search(self, query: str) -> str:
        """
        Search anime information from vector database using RAG.
        Useful for answering questions about anime synopsis, characters, and plot.

        Args:
            query: The search query about anime
        """
        try:
            results = self.rag_service.query(query, top_k=5)
            if not results:
                return "未找到相关动漫信息"

            formatted_results = []
            for i, r in enumerate(results):
                formatted_results.append(f"""
                    {i+1}. 《{r['title']}》
                    评分: 向量相似度 {r['vector_score']:.4f}, 重排分数 {r['rerank_score']:.4f}
                    简介: {r['synopsis']}
                    """)

            return "\n".join(formatted_results)
        except Exception as e:
            error_msg = f"RAG查询失败: {str(e)}"
            print(f"[RAGTool] {error_msg}")
            return error_msg

    async def chat_stream_with_context(self, user_message: str, thread_id: str):
        """
        流式聊天处理函数（无状态模式）

        1. 组装上下文（取最近 6 条历史 + 摘要）
        2. 保存用户消息到 MySQL
        3. 调 LLM → 流式返回
        4. 保存 assistant 回复到 MySQL
        5. 更新对话时间戳

        :param user_message: 用户消息
        :param thread_id: 对话线程ID
        :return: 异步生成器，产生消息事件
        """
        # ① 准备上下文（此时尚未保存当前消息，recent 取的是之前的 6 条）
        model_input = await self._prepare_model_input(thread_id, user_message)

        # ② 保存用户消息
        await asyncio.to_thread(save_message, thread_id, "user", user_message)

        full_response: list[str] = []  # 收集完整回复用于持久化

        try:
            queue: asyncio.Queue = asyncio.Queue()

            def sync_stream_producer():
                try:
                    for chunk, _data in self.agent.stream(
                        {"messages": model_input},
                        stream_mode="messages",
                    ):
                        queue.put_nowait(("message", chunk))
                except Exception as e:
                    queue.put_nowait(("error", e))
                finally:
                    queue.put_nowait(("done", None))

            # 在线程中启动同步流式生产者
            asyncio.get_event_loop().run_in_executor(None, sync_stream_producer)

            # 异步消费队列
            while True:
                stream_mode, chunk = await queue.get()
                if stream_mode == "done":
                    break
                elif stream_mode == "error":
                    raise chunk
                elif stream_mode == "message":
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

        finally:
            # ③ 保存 assistant 回复（即使出错也要保存已有的部分）
            full_text = "".join(full_response)
            if full_text:
                await asyncio.to_thread(save_message, thread_id, "assistant", full_text)

            # ④ 更新对话时间戳
            await asyncio.to_thread(update_chat_updated_at, thread_id)

            # ⑤ 检查是否需要滚动摘要压缩
            await self._maybe_compress(thread_id, user_message)

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
