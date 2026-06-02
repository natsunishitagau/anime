import psycopg
from datetime import datetime
from typing import Optional, List, Dict

def get_db_connection():
    """获取数据库连接"""
    return psycopg.connect(
        host="localhost",
        port=5432,
        user="postgres",
        password="VMware@14",
        dbname="anime_questions",
        autocommit=True
    )

def create_user_chat(user_id: int, thread_id: str, title: str) -> bool:
    """
    创建用户对话关联记录
    :param user_id: 用户ID
    :param thread_id: 对话线程ID
    :param title: 对话标题
    :return: 是否成功
    """
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO user_chats (user_id, thread_id, title, updated_at)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (thread_id) DO UPDATE SET user_id = %s, title = %s, updated_at = %s
                """,
                (user_id, thread_id, title, datetime.now(), user_id, title, datetime.now())
            )
        conn.close()
        return True
    except Exception as e:
        print(f"[UserChats] 创建用户对话失败: {e}")
        return False

def get_user_chats(user_id: int) -> List[Dict]:
    """
    获取用户的所有对话列表
    :param user_id: 用户ID
    :return: 对话列表
    """
    chats = []
    try:
        conn = get_db_connection()
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
            rows = cur.fetchall()
            for row in rows:
                chats.append({
                    'thread_id': row[0],
                    'title': row[1],
                    'updated_at': row[2].isoformat() if row[2] else None
                })
        conn.close()
    except Exception as e:
        print(f"[UserChats] 获取用户对话列表失败: {e}")
    return chats

def update_chat_updated_at(thread_id: str) -> bool:
    """
    更新对话的updated_at时间戳
    :param thread_id: 对话线程ID
    :return: 是否成功
    """
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE user_chats
                SET updated_at = %s
                WHERE thread_id = %s
                """,
                (datetime.now(), thread_id)
            )
        conn.close()
        return True
    except Exception as e:
        print(f"[UserChats] 更新updated_at失败: {e}")
        return False

def delete_user_chat(thread_id: str) -> bool:
    """
    删除用户对话关联记录
    :param thread_id: 对话线程ID
    :return: 是否成功
    """
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            cur.execute(
                """
                DELETE FROM user_chats
                WHERE thread_id = %s
                """,
                (thread_id,)
            )
        conn.close()
        return True
    except Exception as e:
        print(f"[UserChats] 删除用户对话失败: {e}")
        return False

def get_chat_by_thread_id(thread_id: str) -> Optional[Dict]:
    """
    根据thread_id获取对话信息
    :param thread_id: 对话线程ID
    :return: 对话信息
    """
    try:
        conn = get_db_connection()
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT user_id, thread_id, title, updated_at
                FROM user_chats
                WHERE thread_id = %s
                """,
                (thread_id,)
            )
            row = cur.fetchone()
            if row:
                return {
                    'user_id': row[0],
                    'thread_id': row[1],
                    'title': row[2],
                    'updated_at': row[3].isoformat() if row[3] else None
                }
        conn.close()
    except Exception as e:
        print(f"[UserChats] 获取对话信息失败: {e}")
    return None