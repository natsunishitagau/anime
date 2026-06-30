"""
上下文管理器 — 话题切换检测 + 滚动摘要压缩

- detect_anime_switch: 检测用户是否切换了讨论的动漫，按 thread_id 隔离
- rolling_summary: 调用 LLM 将旧摘要与新消息合并为滚动摘要（话题切换时标注）
"""

import re
from typing import Optional, List, Dict

COMPRESS_EVERY_N = 10  # 每 N 条消息触发一次压缩


class ContextManager:
    """按线程管理对话上下文：话题追踪 + 摘要生成"""

    def __init__(self):
        self._thread_anime: dict[str, str] = {}
        self._summary_llm = None

    # ── 话题检测 ──────────────────────────────────────────────

    def extract_anime_name(self, user_message: str) -> Optional[str]:
        """从用户消息中提取并规范化动漫名称"""
        patterns = [
            # 书名号：《进击的巨人》 → "进击的巨人"
            (r"《(.+?)》", 1),
            # 说说/介绍/讲讲/聊聊 XXX
            (r"(?:说说|介绍|讲讲|聊聊)(.+?)(?:的|有关|相关)?(?:剧情|角色|故事|内容)?$", 1),
            # "XXX的主角/角色/剧情/结局/世界观..." → "XXX"
            (r"^(.+?)的(?:主角|角色|剧情|结局|世界观|设定|能力|故事|声优|导演|制作)", 1),
            # "XXX好不好看/好看吗/怎么样/是什么/讲了什么" → "XXX"
            (r"^(.+?)(?:好不好看|好看吗|怎么样|是什么|讲了什么|讲的是什么)", 1),
        ]
        for pattern, group_idx in patterns:
            match = re.search(pattern, user_message)
            if match:
                name = match.group(group_idx).strip()
                # 循环清理尾部常见后缀
                while True:
                    new_name = re.sub(
                        r"(?:的结局|的人物|的角色|的剧情|的五档|的能力|的形态|的招式"
                        r"|的|了|吗|呢|啊|吧|什么|怎么"
                        r"|结局|剧情|人物|角色|五档|能力|形态)$",
                        "", name
                    ).strip()
                    if new_name == name:
                        break
                    name = new_name
                # 代词检查：排除纯代词和代词前缀
                PRONOUN_PREFIXES = {"他的", "她的", "它的", "他们的", "她们的", "它们的", "这个的", "那个的"}
                PRONOUN_BARE = {"他", "她", "它", "他们", "她们", "它们",
                                "这个", "那个", "这部", "那部", "这", "那",
                                "我", "你", "你们", "我们", "大家",
                                "什么", "怎么", "为什么", "哪个", "哪里"}
                has_pronoun_prefix = any(name.startswith(p) for p in PRONOUN_PREFIXES)
                is_bare_pronoun = name in PRONOUN_BARE
                if name and len(name) >= 2 and not has_pronoun_prefix and not is_bare_pronoun:
                    return name
        return None

    def _name_matches(self, a: str, b: str) -> bool:
        """判断两个动漫名称是否指向同一部作品"""
        a, b = a.lower().strip(), b.lower().strip()
        return a == b or a in b or b in a

    def detect_anime_switch(self, user_message: str, thread_id: str) -> tuple:
        """
        检测用户是否切换了讨论的动漫

        Returns:
            (is_switch: bool, anime_name: str | None)
            - is_switch=True: 用户切换了话题，anime_name 为新动漫名
            - is_switch=False: 未切换或首次设置
        """
        new_anime = self.extract_anime_name(user_message)
        current = self._thread_anime.get(thread_id)

        # 切换：之前有话题，且新话题不同（用包含关系判断，避免不同提问方式误判）
        if new_anime and current and not self._name_matches(new_anime, current):
            self._thread_anime[thread_id] = new_anime
            return True, new_anime

        # 首次设置或相同话题
        if new_anime:
            self._thread_anime[thread_id] = new_anime

        return False, new_anime

    def get_current_anime(self, thread_id: str) -> Optional[str]:
        """获取当前线程正在讨论的动漫名称"""
        return self._thread_anime.get(thread_id)

    def clear_thread(self, thread_id: str):
        """清理指定线程的动漫追踪状态"""
        self._thread_anime.pop(thread_id, None)

    # ── 滚动摘要 ──────────────────────────────────────────────

    async def rolling_summary(
        self,
        old_summary: str | None,
        recent_messages: List[Dict],
        switch_info: str | None = None,
    ) -> str:
        """
        调用 LLM 将旧摘要与新消息合并为滚动摘要。

        Args:
            old_summary: 上一次的摘要（None 表示首次压缩）
            recent_messages: 最近的消息列表 [{"role": "...", "content": "..."}, ...]
            switch_info: 话题切换标注，如 "anime_switch:进击的巨人"

        Returns:
            新的摘要文本（300 字以内）
        """
        if not self._summary_llm:
            from app.models.chat import llm
            self._summary_llm = llm

        # 构建消息文本（截断每条防止太长）
        msg_lines = []
        for m in recent_messages:
            content = m["content"][:300] if len(m["content"]) > 300 else m["content"]
            role_label = "用户" if m["role"] == "user" else "助手"
            msg_lines.append(f"{role_label}: {content}")
        msg_text = "\n".join(msg_lines)

        # 话题切换标注
        switch_note = ""
        if switch_info:
            anime_name = switch_info.split(":", 1)[1] if ":" in switch_info else switch_info
            switch_note = (
                f"\n【重要】用户切换了讨论话题，新话题为《{anime_name}》。"
                f"请在摘要中明确标注此话题切换。\n"
            )

        prompt = f"""请将以下内容合并成一个简洁的对话摘要（300字以内，中文）：
        {'【已有摘要】' + old_summary if old_summary else '【首次对话记录】'}
        {switch_note}
        【最近对话】
        {msg_text}

        要求：
        - 保留关键信息：动漫名称、剧情讨论要点、用户提出的问题和观点
        - 如果标注了话题切换，摘要中必须包含"话题切换：从XXX到XXX"或类似描述
        - 语言简洁，不超过300字
        - 用第三人称描述，例如"用户询问了..."、"助手解释了..."
        - 不要遗漏任何用户明确表达过的偏好或评价"""

        try:
            response = await self._summary_llm.ainvoke([{"role": "user", "content": prompt}])
            summary = response.content if hasattr(response, "content") else str(response)
            return summary.strip()
        except Exception as e:
            print(f"[ContextManager] 摘要生成失败: {e}")
            # 降级：返回旧摘要（如果有的话）
            return old_summary or ""


# 全局单例
context_manager = ContextManager()
