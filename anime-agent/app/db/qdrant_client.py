from qdrant_client import QdrantClient

_qdrant_client = None


def get_qdrant_client(path: str = "./qdrant_storage") -> QdrantClient:
    """
    获取 QdrantClient 全局单例实例
    
    Args:
        path: Qdrant 存储路径
        
    Returns:
        QdrantClient 单例实例
    """
    global _qdrant_client
    
    if _qdrant_client is None:
        _qdrant_client = QdrantClient(path=path)
    
    return _qdrant_client


def reset_qdrant_client():
    """重置 QdrantClient 单例（主要用于测试）"""
    global _qdrant_client
    _qdrant_client = None
