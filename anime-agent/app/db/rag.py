import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 开启离线模式，避免从网络下载模型
os.environ['TRANSFORMERS_OFFLINE'] = '1'
os.environ['SENTENCE_TRANSFORMERS_HOME'] = './models'

from typing import List, Dict
from sentence_transformers import SentenceTransformer, CrossEncoder

from app.db.build_qdrant_vector_db import COLLECTION_NAME
from app.db.qdrant_client import get_qdrant_client

# Rerank 模型
RERANK_MODEL_NAME = "BAAI/bge-reranker-v2-m3"

# 本地模型路径
LOCAL_EMBEDDING_MODEL_PATH = "C:\\Users\\20453\\.cache\\huggingface\\hub\\models--BAAI--bge-large-zh\\snapshots"
LOCAL_RERANK_MODEL_PATH = "C:\\Users\\20453\\.cache\\huggingface\\hub\\models--BAAI--bge-reranker-v2-m3\\snapshots"


class AnimeRAG:
    _instance = None
    _initialized = False

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(AnimeRAG, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        
        self.embedding_model = SentenceTransformer(LOCAL_EMBEDDING_MODEL_PATH)
        self.rerank_model = CrossEncoder(LOCAL_RERANK_MODEL_PATH)
        self.qdrant_client = get_qdrant_client()
        
        self._initialized = True
    
    def embed_text(self, text: str) -> List[float]:
        """文本向量化"""
        embedding = self.embedding_model.encode(text)
        return embedding.tolist()
    
    def _search_vector(self, query: str, top_k: int = 20) -> List[Dict]:
        """向量检索（初步检索较多结果用于rerank）"""
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
                "vector_score": point.score
            }
            for point in points
        ]
    
    def _rerank(self, query: str, candidates: List[Dict], top_k: int = 5) -> List[Dict]:
        """重排：使用 CrossEncoder 对向量检索结果二次排序"""
        if not candidates:
            return []
        
        pairs = [
            (query, f"{c['title']} {c['synopsis']}") 
            for c in candidates
        ]
        
        scores = self.rerank_model.predict(pairs)
        
        for candidate, score in zip(candidates, scores):
            candidate["rerank_score"] = float(score)
        
        candidates_sorted = sorted(
            candidates, 
            key=lambda x: x["rerank_score"], 
            reverse=True
        )
        
        return candidates_sorted[:top_k]
    
    def query(self, query: str, top_k: int = 5, vector_top_k: int = 10) -> List[Dict]:
        """完整的 RAG 查询流程：向量检索 → Rerank → 结果返回"""
        candidates = self._search_vector(query, top_k=vector_top_k)
        results = self._rerank(query, candidates, top_k=top_k)
        return results


def create_rag_service() -> AnimeRAG:
    """创建 RAG 服务实例（单例）"""
    return AnimeRAG()


if __name__ == "__main__":
    print("=== Testing RAG Service ===")
    
    rag = create_rag_service()
    
    test_queries = [
        "银魂"
    ]
    
    try:
        for query in test_queries:
            print(f"\nQuery: {query}")
            results = rag.query(query)
            for i, r in enumerate(results):
                print(f"  {i+1}. [V: {r['vector_score']:.4f}, R: {r['rerank_score']:.4f}] {r['title']}")
    finally:
        rag = None
        import gc
        gc.collect()
    print("\n=== Test Complete ===")
