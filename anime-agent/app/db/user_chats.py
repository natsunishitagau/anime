"""
MySQL 对话持久化模块

表结构:
- user_chats: 用户-对话映射，含标题和摘要
- conversation_messages: 对话消息全量存储
"""

import pymysql
from datetime import datetime
from typing import Optional, List, Dict
from contextlib import contextmanager

DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "anime_user",
    "password": "Anime@123",
    "database": "anime_db",
    "charset": "utf8mb4",
}

CREATE_TABLES_SQL = """
CREATE TABLE IF NOT EXISTS user_chats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    thread_id VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(255) DEFAULT '',
    summary TEXT DEFAULT NULL,
    next_compress_at INT DEFAULT 10,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_thread_id (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thread_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL COMMENT 'user / assistant / tool / system',
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_thread_created (thread_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""

# 兼容旧表：添加缺失的列
MIGRATE_SQL = [
    "ALTER TABLE user_chats ADD COLUMN summary TEXT DEFAULT NULL",
    "ALTER TABLE user_chats ADD COLUMN next_compress_at INT DEFAULT 10",
]


@contextmanager
def get_db():
    """获取 MySQL 数据库连接（上下文管理器，自动提交/回滚）"""
    conn = pymysql.connect(**DB_CONFIG)
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def init_db():
    """初始化数据库表（首次启动时调用，兼容旧表迁移）"""
    try:
        conn = pymysql.connect(**DB_CONFIG)
        with conn.cursor() as cur:
            for stmt in CREATE_TABLES_SQL.split(";"):
                stmt = stmt.strip()
                if stmt:
                    cur.execute(stmt + ";")
            # 兼容旧表：尝试添加新列（忽略"列已存在"错误）
            for stmt in MIGRATE_SQL:
                try:
                    cur.execute(stmt)
                except Exception:
                    pass  # 列已存在，跳过
        conn.commit()
        conn.close()
        print("[DB] MySQL 表初始化完成")
    except Exception as e:
        print(f"[DB] 表初始化失败: {e}")


# ─── user_chats 表操作 ───────────────────────────────────────


def create_user_chat(user_id: int, thread_id: str, title: str) -> bool:
    """创建用户对话关联记录"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO user_chats (user_id, thread_id, title, updated_at)
                    VALUES (%s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE user_id = %s, title = %s, updated_at = %s
                    """,
                    (user_id, thread_id, title, datetime.now(),
                     user_id, title, datetime.now())
                )
        return True
    except Exception as e:
        print(f"[DB] 创建用户对话失败: {e}")
        return False


def get_user_chats(user_id: int) -> List[Dict]:
    """获取用户的所有对话列表（按更新时间倒序）"""
    chats = []
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT thread_id, title, updated_at
                    FROM user_chats
                    WHERE user_id = %s
                    ORDER BY updated_at DESC
                    """,
                    (user_id,)
                )
                for row in cur.fetchall():
                    chats.append({
                        "thread_id": row[0],
                        "title": row[1],
                        "updated_at": row[2].isoformat() if row[2] else None
                    })
    except Exception as e:
        print(f"[DB] 获取用户对话列表失败: {e}")
    return chats


def update_chat_updated_at(thread_id: str) -> bool:
    """更新对话的 updated_at 时间戳"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "UPDATE user_chats SET updated_at = %s WHERE thread_id = %s",
                    (datetime.now(), thread_id)
                )
        return True
    except Exception as e:
        print(f"[DB] 更新 updated_at 失败: {e}")
        return False


def update_chat_summary(thread_id: str, summary: str) -> bool:
    """保存对话压缩摘要"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "UPDATE user_chats SET summary = %s WHERE thread_id = %s",
                    (summary, thread_id)
                )
        return True
    except Exception as e:
        print(f"[DB] 保存摘要失败: {e}")
        return False


def get_chat_summary(thread_id: str) -> Optional[str]:
    """获取对话压缩摘要"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT summary FROM user_chats WHERE thread_id = %s",
                    (thread_id,)
                )
                row = cur.fetchone()
                if row and row[0]:
                    return row[0]
    except Exception as e:
        print(f"[DB] 获取摘要失败: {e}")
    return None


def delete_user_chat(thread_id: str) -> bool:
    """删除用户对话关联记录及其所有消息"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute("DELETE FROM user_chats WHERE thread_id = %s", (thread_id,))
                cur.execute("DELETE FROM conversation_messages WHERE thread_id = %s", (thread_id,))
        return True
    except Exception as e:
        print(f"[DB] 删除用户对话失败: {e}")
        return False


def get_chat_by_thread_id(thread_id: str) -> Optional[Dict]:
    """根据 thread_id 获取对话信息"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT user_id, thread_id, title, summary, updated_at
                    FROM user_chats WHERE thread_id = %s
                    """,
                    (thread_id,)
                )
                row = cur.fetchone()
                if row:
                    return {
                        "user_id": row[0],
                        "thread_id": row[1],
                        "title": row[2],
                        "summary": row[3],
                        "updated_at": row[4].isoformat() if row[4] else None
                    }
    except Exception as e:
        print(f"[DB] 获取对话信息失败: {e}")
    return None


# ─── conversation_messages 表操作 ─────────────────────────────


def save_message(thread_id: str, role: str, content: str) -> bool:
    """保存一条对话消息"""
    if not content:
        return False
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "INSERT INTO conversation_messages (thread_id, role, content) VALUES (%s, %s, %s)",
                    (thread_id, role, content)
                )
        return True
    except Exception as e:
        print(f"[DB] 保存消息失败: {e}")
        return False


def get_recent_messages(thread_id: str, limit: int = 6) -> List[Dict]:
    """获取指定线程最近 N 条消息（按时间正序）"""
    messages = []
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT role, content, created_at
                    FROM (
                        SELECT role, content, created_at
                        FROM conversation_messages
                        WHERE thread_id = %s
                        ORDER BY created_at DESC
                        LIMIT %s
                    ) recent
                    ORDER BY created_at ASC
                    """,
                    (thread_id, limit)
                )
                for row in cur.fetchall():
                    messages.append({
                        "role": row[0],
                        "content": row[1],
                        "created_at": row[2].isoformat() if row[2] else None
                    })
    except Exception as e:
        print(f"[DB] 获取最近消息失败: {e}")
    return messages


def get_all_messages(thread_id: str, limit: int = 1000) -> List[Dict]:
    """获取指定线程所有消息（用于历史浏览，带上限保护）"""
    messages = []
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT role, content, created_at
                    FROM conversation_messages
                    WHERE thread_id = %s
                    ORDER BY created_at ASC
                    LIMIT %s
                    """,
                    (thread_id, limit)
                )
                for row in cur.fetchall():
                    messages.append({
                        "role": row[0],
                        "content": row[1],
                        "created_at": row[2].isoformat() if row[2] else None
                    })
    except Exception as e:
        print(f"[DB] 获取全部消息失败: {e}")
    return messages


def delete_thread_messages(thread_id: str) -> bool:
    """删除指定线程的所有消息"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute("DELETE FROM conversation_messages WHERE thread_id = %s", (thread_id,))
        return True
    except Exception as e:
        print(f"[DB] 删除消息失败: {e}")
        return False


# ─── 压缩状态管理 ─────────────────────────────────────────────


def count_thread_messages(thread_id: str) -> int:
    """统计指定线程的消息总数"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT COUNT(*) FROM conversation_messages WHERE thread_id = %s",
                    (thread_id,)
                )
                row = cur.fetchone()
                return row[0] if row else 0
    except Exception as e:
        print(f"[DB] 统计消息数失败: {e}")
        return 0


def get_compress_state(thread_id: str) -> tuple:
    """获取压缩状态: (summary, next_compress_at)，不存在则返回 (None, 10)"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT summary, next_compress_at FROM user_chats WHERE thread_id = %s",
                    (thread_id,)
                )
                row = cur.fetchone()
                if row:
                    return (row[0], row[1] if row[1] is not None else 10)
    except Exception as e:
        print(f"[DB] 获取压缩状态失败: {e}")
    return (None, 10)


def save_compress_state(thread_id: str, summary: str, next_compress_at: int) -> bool:
    """保存压缩结果：摘要 + 下次触发点"""
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """UPDATE user_chats
                       SET summary = %s, next_compress_at = %s, updated_at = %s
                       WHERE thread_id = %s""",
                    (summary, next_compress_at, datetime.now(), thread_id)
                )
        return True
    except Exception as e:
        print(f"[DB] 保存压缩状态失败: {e}")
        return False
