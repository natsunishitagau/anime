import re
import time
from collections import OrderedDict
from typing import List, Optional, Tuple
from langchain_core.messages import SystemMessage, BaseMessage
from dataclasses import dataclass, field

MAX_TOKENS = 6000
COMPRESSION_THRESHOLD = 0.8
ANIME_KEYWORDS = ["动漫", "番剧", "动画", "这部", "那部", "第", "部", "名字", "角色", "主角"]

@dataclass
class ConversationSummary:
    summary: str
    remaining_messages: List[BaseMessage] = field(default_factory=list)

class ContextManager:
    def __init__(self, max_tokens: int = MAX_TOKENS, compression_threshold: float = COMPRESSION_THRESHOLD):
        self.max_tokens = max_tokens
        self.compression_threshold = compression_threshold
        self.current_anime: Optional[str] = None
        self._summary_llm = None

    def set_llm(self, llm):
        self._summary_llm = llm

    def estimate_tokens(self, messages: List[BaseMessage]) -> int:
        total_tokens = 0
        for msg in messages:
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            chinese_chars = len(re.findall(r'[\u4e00-\u9fff]', content))
            non_chinese_chars = len(content) - chinese_chars
            tokens = int(chinese_chars * 1.5 + non_chinese_chars / 4)
            total_tokens += tokens
        return total_tokens

    def extract_anime_name(self, user_message: str) -> Optional[str]:
        patterns = [
            r"(?:说说|介绍|讲讲|聊聊)(.+?)(?:的|有关|相关)?(?:剧情|角色|故事|内容)?",
            r"(?:第(\d+)部?)",
            r"《(.+?)》",
        ]
        for pattern in patterns:
            match = re.search(pattern, user_message)
            if match:
                return match.group(0)
        return None

    def detect_anime_switch(self, user_message: str) -> bool:
        new_anime = self.extract_anime_name(user_message)
        if new_anime and self.current_anime and new_anime != self.current_anime:
            return True
        if new_anime:
            self.current_anime = new_anime
        return False

    async def compress_context(self, messages: List[BaseMessage]) -> Tuple[List[BaseMessage], str]:
        system_msg = None
        non_system = []
        for msg in messages:
            if isinstance(msg, SystemMessage):
                system_msg = msg
            else:
                non_system.append(msg)

        if len(non_system) <= 2:
            return messages, ""

        summary_prompt = f"""请总结以下对话的要点，保留关键信息以便后续对话能继续：

{' '.join([f'{msg.type}: {msg.content if isinstance(msg.content, str) else str(msg.content)}' for msg in non_system])}

简洁总结（200字以内）："""

        if not self._summary_llm:
            from app.models.chat import llm
            self._summary_llm = llm

        response = await self._summary_llm.ainvoke([{"role": "user", "content": summary_prompt}])
        summary = response.content if hasattr(response, 'content') else str(response)

        compressed = [system_msg] if system_msg else []
        compressed.append(SystemMessage(content=f"[对话摘要] {summary}"))
        compressed.extend(non_system[-2:])

        return compressed, summary

    def should_compress(self, messages: List[BaseMessage], user_message: str) -> bool:
        if self.detect_anime_switch(user_message):
            return True
        token_count = self.estimate_tokens(messages)
        return token_count >= self.max_tokens * self.compression_threshold

context_manager = ContextManager()

class CompressionSummaryStore:
    """压缩摘要存储类，使用LRU策略和TTL机制管理对话摘要的缓存"""

    def __init__(self, max_size: int = 50000, ttl_seconds: int = 900):
        """
        初始化压缩摘要存储
        :param max_size: 存储的最大条目数，超过时自动淘汰最旧的条目
        :param ttl_seconds: 条目过期时间（秒），默认15分钟
        """
        self._store: OrderedDict[str, tuple[str, float]] = OrderedDict()
        self.max_size = max_size
        self.ttl_seconds = ttl_seconds

    def _is_expired(self, timestamp: float) -> bool:
        """
        检查时间戳是否已过期
        :param timestamp: 要检查的时间戳
        :return: 如果已过期返回True，否则返回False
        """
        return time.time() - timestamp > self.ttl_seconds

    def _evict_if_needed(self):
        """当存储大小达到上限时，淘汰最旧的条目（LRU策略）"""
        while len(self._store) >= self.max_size:
            self._store.popitem(last=False)

    def get(self, thread_id: str) -> Optional[str]:
        """
        根据线程ID获取压缩摘要
        :param thread_id: 线程唯一标识
        :return: 压缩摘要内容，如果不存在或已过期返回None
        """
        if thread_id not in self._store:
            return None
        content, timestamp = self._store[thread_id]
        if self._is_expired(timestamp):
            del self._store[thread_id]
            return None
        self._store.move_to_end(thread_id)

        return content

    def set(self, thread_id: str, summary: str):
        """
        设置线程的压缩摘要
        :param thread_id: 线程唯一标识
        :param summary: 压缩摘要内容
        """
        self._evict_if_needed()
        self._store[thread_id] = (summary, time.time())
        self._store.move_to_end(thread_id)

    def touch(self, thread_id: str):
        """
        更新线程条目的时间戳，延长其有效期
        :param thread_id: 线程唯一标识
        """
        if thread_id in self._store:
            content, _ = self._store[thread_id]
            self._store[thread_id] = (content, time.time())
            self._store.move_to_end(thread_id)

    def get_recent_summary_with_messages(self, thread_id: str, messages: List[BaseMessage], keep_recent: int = 4) -> List[BaseMessage]:
        self.touch(thread_id)
        summary = self.get(thread_id)
        if not summary:
            return messages[-keep_recent:] if len(messages) > keep_recent else messages
        summary_msg = SystemMessage(content=f"[对话摘要] {summary}")
        recent = messages[-keep_recent:] if len(messages) > keep_recent else messages
        return [summary_msg] + recent

summary_store = CompressionSummaryStore()