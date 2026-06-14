from math import e

from langchain_community.utilities import SearxSearchWrapper
from langchain_core.tools.simple import Tool
from langchain.agents import create_agent
from langchain_core.messages import HumanMessage, AIMessage, ToolMessage
from langgraph.checkpoint.postgres import PostgresSaver
import psycopg
import asyncio
from app.models.chat import llm
from app.agent.context_manager import context_manager, summary_store
from app.db.rag import create_rag_service


class AnimeMaster:
    """
    动漫助手核心类，封装搜索、RAG查询和对话管理功能

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
        self._init_checkpointer()
        self._init_agent()

    def _init_rag_service(self):
        """初始化 RAG 服务"""
        self.rag_service = create_rag_service()

    def _init_searx_wrapper(self):
        """初始化 Searx 搜索包装器"""
        self.searx_wrapper = SearxSearchWrapper(
            searx_host="http://localhost:4000",
            engines=["360search","sougou","bing"],
            k=5
        )

    def _init_checkpointer(self):
        """初始化 PostgreSQL 检查点"""
        conn = psycopg.connect(
            host="localhost",
            port=5432,
            user="postgres",
            password="VMware@14",
            dbname="anime_questions",
            autocommit=True
        )
        self.checkpointer = PostgresSaver(conn)
        self.checkpointer.setup()

    def _init_agent(self):
        """初始化智能代理"""
        # 使用 Tool.from_function 创建工具，避免 @tool 装饰器在实例方法上的问题
        search_tool = Tool.from_function(
            func=self.search,
            name="search",
            description="Search the web using SearxNG meta search engine. Useful when you need to find current information or facts.",
        )
        # rag_tool = Tool.from_function(
        #     func=self.rag_search,
        #     name="rag_search",
        #     description="Search anime information from vector database using RAG. Useful for answering questions about anime synopsis.",
        # )
        self.agent = create_agent(
            model=llm,
            tools=[search_tool],
            # tools=[search_tool, rag_tool],
            system_prompt=self.SYSTEM_PROMPT,
            checkpointer=self.checkpointer,
        )

    def _get_thread_config(self, thread_id: str) -> dict:
        """获取线程配置"""
        return {"configurable": {"thread_id": thread_id}}

    def _get_messages_from_state(self, state) -> list:
        """从检查点状态中提取消息列表"""
        current_messages = []
        if state and isinstance(state, dict):
            channel_values = state.get("channel_values", {})
            if "messages" in channel_values:
                current_messages = list(channel_values["messages"]) if channel_values["messages"] else []
            elif isinstance(channel_values, dict):
                current_messages = list(channel_values.get("messages", [])) if channel_values.get("messages") else []
        return current_messages

    async def _prepare_model_input(self, thread_id: str, user_message: str) -> list:
        """准备模型输入，处理上下文压缩"""
        config = self._get_thread_config(thread_id)
        # 转为异步执行
        state = await asyncio.to_thread(self.checkpointer.get, config)
        # 返回的状态中提取消息
        current_messages = self._get_messages_from_state(state)

        message = HumanMessage(content=user_message)
        new_messages = current_messages + [message] if current_messages else [message]

        # 检查是否需要压缩上下文
        if context_manager.should_compress(new_messages, user_message) and not summary_store.get(thread_id):
            compressed, summary = await context_manager.compress_context(new_messages)
            summary_store.set(thread_id, summary)

        return summary_store.get_recent_summary_with_messages(thread_id, new_messages)

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
        流式聊天处理函数
        :param user_message: 用户消息
        :param thread_id: 对话线程ID
        :return: 异步生成器，产生消息块
        """
        try:
            model_input = await self._prepare_model_input(thread_id, user_message)
            config = self._get_thread_config(thread_id)
            
            # 使用队列实现真正的流式传输
            queue = asyncio.Queue()
            
            def sync_stream_producer():
                try:
                    for chunk, data in self.agent.stream(
                        {"messages": model_input},
                        config,
                        stream_mode="messages",
                    ):
                        queue.put_nowait(('message',chunk))
                except Exception as e:
                    queue.put_nowait(('error', e))
                finally:
                    queue.put_nowait(('done', None))
            
            # 在线程中启动同步流式生产者
            asyncio.get_event_loop().run_in_executor(None, sync_stream_producer)
            
            # 异步消费队列
            while True:
                stream_mode, chunk = await queue.get()
                if stream_mode == 'done':
                    break
                elif stream_mode == 'error':
                    raise chunk
                elif stream_mode == 'message':
                    if chunk.content=='':
                        try:
                            if chunk.tool_calls:
                                yield {
                                    "type": "tool_call",
                                    "content": "调用搜索工具",
                                }
                            else:
                                yield {
                                    "type": "consider",
                                    "content": "思考中",
                                }
                        except:
                            yield {
                                "type": "consider",
                                "content": "思考中",
                            }
                    else:
                        if isinstance(chunk,AIMessage):
                            yield {"type": "content", "content": chunk.content}
                        
        except Exception as e:
            print(f"[Stream] Error: {type(e).__name__}: {e}")
            import traceback
            traceback.print_exc()
            raise

    async def get_conversation_history(self, thread_id: str):
        """
        获取对话历史
        :param thread_id: 对话线程ID
        :return: 消息列表，如果不存在返回None
        """
        config = self._get_thread_config(thread_id)
        state = await asyncio.to_thread(self.checkpointer.get, config)

        if not state:
            return None

        return self._get_messages_from_state(state)

    async def delete_conversation_history(self, thread_id: str) -> bool:
        """
        删除对话历史
        :param thread_id: 对话线程ID
        :return: 是否成功删除
        """
        config = self._get_thread_config(thread_id)
        state = await asyncio.to_thread(self.checkpointer.get, config)

        if not state:
            return False

        await asyncio.to_thread(self.checkpointer.delete_thread, thread_id)
        if thread_id in summary_store._store:
            del summary_store._store[thread_id]

        return True


# 全局实例，保持向后兼容
anime_master = AnimeMaster()

# 保持原有的函数接口，向后兼容
search = anime_master.search
rag_search = anime_master.rag_search


async def chat_with_context(user_message: str, thread_id: str) -> dict:
    return await anime_master.chat_with_context(user_message, thread_id)


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
    pass