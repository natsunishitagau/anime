import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pymysql
pymysql.install_as_MySQLdb()
import MySQLdb
from typing import List, Dict, Any
from sentence_transformers import SentenceTransformer
from qdrant_client.models import Distance, VectorParams, PointStruct
import uuid

from app.db.qdrant_client import get_qdrant_client

DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "anime_user",
    "password": "Anime@123",
    "database": "anime_db",
    "charset": "utf8mb4"
}

COLLECTION_NAME = "anime_synopsis"
EMBEDDING_MODEL_NAME = "BAAI/bge-large-zh"
EMBEDDING_DIM = 1024

class QdrantAnimeVectorDB:
    def __init__(self, db_config: Dict[str, Any]):
        self.db_config = db_config
        self.conn = None
        self.cursor = None
        self.embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)
        self.qdrant_client = get_qdrant_client()
        self._create_collection()

    def _create_collection(self):
        collections = self.qdrant_client.get_collections().collections
        exists = COLLECTION_NAME in [c.name for c in collections]
        
        if exists:
            print(f"Collection {COLLECTION_NAME} already exists")
            return
        
        try:
            self.qdrant_client.delete_collection(collection_name=COLLECTION_NAME)
            print(f"Deleted existing collection: {COLLECTION_NAME}")
        except Exception:
            pass

        self.qdrant_client.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(size=EMBEDDING_DIM, distance=Distance.COSINE),
        )
        print(f"Created collection: {COLLECTION_NAME}")

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

    def read_all_anime_with_chinese_synopsis(self) -> List[Dict]:
        query = """
            SELECT id, title, synopsis
            FROM anime
            WHERE synopsis IS NOT NULL
              AND synopsis != ''
              AND CHAR_LENGTH(synopsis) > 10
        """
        self.cursor.execute(query)
        results = self.cursor.fetchall()
        print(f"Read {len(results)} anime records with synopsis")
        return results

    def embed_text(self, text: str) -> List[float]:
        embedding = self.embedding_model.encode(text)
        return embedding.tolist()

    def build_vector_db(self, batch_size: int = 100):
        self.connect()

        try:
            anime_data = self.read_all_anime_with_chinese_synopsis()

            points = []
            for i, anime in enumerate(anime_data):
                synopsis = anime['synopsis']
                title = anime['title']
                anime_id = anime['id']

                embedding = self.embed_text(synopsis)

                point = PointStruct(
                    id=str(uuid.uuid4()),
                    vector=embedding,
                    payload={
                        "anime_id": anime_id,
                        "title": title,
                        "synopsis": synopsis,
                        "type": "synopsis",
                        "language": "zh"
                    }
                )
                points.append(point)

                if len(points) >= batch_size or i == len(anime_data) - 1:
                    self.qdrant_client.upsert(
                        collection_name=COLLECTION_NAME,
                        points=points
                    )
                    print(f"Uploaded {len(points)} points to Qdrant (total: {i + 1}/{len(anime_data)})")
                    points = []

            print(f"\n=== Vector DB Build Complete ===")
            print(f"Collection: {COLLECTION_NAME}")
            print(f"Total points: {len(anime_data)}")
            print(f"Embedding model: {EMBEDDING_MODEL_NAME}")
            print(f"Dimension: {EMBEDDING_DIM}")

        except Exception as e:
            print(f"Error: {e}")
            if self.conn:
                self.conn.rollback()
        finally:
            self.close()

    def search(self, query: str, top_k: int = 5) -> List[Dict]:
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
                "score": point.score
            }
            for point in points
        ]

def main():
    print("Building Qdrant vector database for anime synopsis...")
    vector_db = QdrantAnimeVectorDB(DB_CONFIG)
    # vector_db.build_vector_db()

    print("\n=== Testing search ===")
    test_queries = ["热血冒险", "校园恋爱", "科幻战斗"]
    for query in test_queries:
        print(f"\nQuery: {query}")
        results = vector_db.search(query, top_k=3)
        for i, result in enumerate(results):
            print(f"  {i+1}. [{result['score']:.4f}] {result['title']}")

if __name__ == "__main__":
    main()
