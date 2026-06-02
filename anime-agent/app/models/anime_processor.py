import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pymysql
pymysql.install_as_MySQLdb()
import MySQLdb
from langchain.chat_models import init_chat_model
from typing import List, Dict, Any
import re

DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "anime_user",
    "password": "Anime@123",
    "database": "anime_db",
    "charset": "utf8mb4"
}

llm = init_chat_model(
    api_key=os.environ.get("DASHSCOPE_API_KEY"),
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    model="openai:qwen-max"
)

def is_chinese_text(text: str) -> bool:
    if not text or not text.strip():
        return False
    
    chinese_chars = re.findall(r'[\u4e00-\u9fff]', text)
    total_chars = len(re.findall(r'[a-zA-Z\u4e00-\u9fff]', text))
    
    if total_chars == 0:
        return False
    
    chinese_ratio = len(chinese_chars) / total_chars
    return chinese_ratio > 0.3


class AnimeDataProcessor:
    def __init__(self, db_config: Dict[str, Any]):
        self.db_config = db_config
        self.conn = None
        self.cursor = None
    
    def connect(self):
        self.conn = MySQLdb.connect(**self.db_config)
        self.cursor = self.conn.cursor(MySQLdb.cursors.DictCursor)
        print(f"Connected to MySQL database: {self.db_config['database']}")
    
    def close(self):
        if self.cursor:
            self.cursor.close()
        if self.conn:
            self.conn.close()
        print("Database connection closed")
    
    def read_anime_data(self, limit: int = 10, offset: int = 0) -> List[Dict]:
        query = f"SELECT id, title, title_jp FROM anime WHERE title_jp IS NOT NULL AND title_jp != '' LIMIT {limit} OFFSET {offset}"
        self.cursor.execute(query)
        results = self.cursor.fetchall()
        print(f"Read {len(results)} records from anime table (offset={offset})")
        return results
    
    def translate_title_to_chinese(self, title_jp: str) -> str:
        if not title_jp or not title_jp.strip():
            return title_jp

        prompt = f"""你是一个专业的动漫标题本地化助手，负责将日文动漫标题翻译为中文。

翻译规则：
- 使用符合中文习惯的简短翻译
- 优先使用 Bangumi / 萌百 常用译名
- 若无通用中文译名，则输出英文译名
- 只输出翻译后的标题，不要解释、不要加引号

日文标题：
{title_jp}"""

        response = llm.invoke(prompt)
        return response.content.strip()
    
    def update_title(self, anime_id: int, new_title: str):
        query = "UPDATE anime SET title = %s WHERE id = %s"
        self.cursor.execute(query, (new_title, anime_id))
        self.conn.commit()
        print(f"Updated anime id={anime_id} title to: {new_title}")
    
    def process_batch(self, batch_size: int = 10, offset: int = 0):
        data = self.read_anime_data(limit=batch_size, offset=offset)

        translated_count = 0
        skipped_count = 0

        for record in data:
            print(f"\nProcessing: {record['title_jp']} (id={record['id']})")

            if is_chinese_text(record['title']):
                print(f"  Skipped (already has Chinese title)")
                skipped_count += 1
                continue

            translated = self.translate_title_to_chinese(record['title_jp'])
            self.update_title(record['id'], translated)
            translated_count += 1

        print(f"\nBatch completed: {translated_count} translated, {skipped_count} skipped")
        return translated_count, skipped_count
    
    def run(self, batch_size: int = 600, total_limit: int = 2063):
        try:
            self.connect()
            
            total_translated = 0
            total_skipped = 0
            offset = 1574
            
            while offset < total_limit:
                translated, skipped = self.process_batch(batch_size, offset)
                total_translated += translated
                total_skipped += skipped
                offset += batch_size
                
                if translated == 0 and skipped == batch_size:
                    print(f"\nAll records in this batch are already Chinese, stopping...")
                    break
            
            print(f"\n=== Final Summary ===")
            print(f"Total translated: {total_translated}")
            print(f"Total skipped (already Chinese): {total_skipped}")
            
        except Exception as e:
            print(f"Error: {e}")
            if self.conn:
                self.conn.rollback()
        finally:
            self.close()

def main():
    processor = AnimeDataProcessor(DB_CONFIG)
    processor.run()

if __name__ == "__main__":
    main()