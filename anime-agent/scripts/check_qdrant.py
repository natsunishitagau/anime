"""Check Qdrant version, collection config, and text index support"""
import sys, io, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from importlib.metadata import version
print(f"Qdrant version: {version('qdrant-client')}")

from qdrant_client import QdrantClient

client = QdrantClient(path="./qdrant_storage")
collections = client.get_collections()
print(f"Collections: {[c.name for c in collections.collections]}")

from app.db.build_qdrant_vector_db import COLLECTION_NAME
info = client.get_collection(COLLECTION_NAME)
print(f"Collection: {COLLECTION_NAME}")
print(f"Points: {info.points_count}")

# Check if query_points supports text/query filtering
print(f"\nQdrant has 'create_payload_index': {hasattr(client, 'create_payload_index')}")
print(f"Qdrant has 'query_points': {hasattr(client, 'query_points')}")

# Test if we can do a simple scroll with filter
try:
    from qdrant_client.models import Filter, FieldCondition, MatchText
    print("Qdrant models (Filter, FieldCondition, MatchText) imported OK")

    # Check if text index creation is supported
    print("Has FieldCondition: OK")
    print("Has MatchText: OK")
except ImportError as e:
    print(f"Import error: {e}")

# Check if local mode supports payload indexes
try:
    from qdrant_client.models import PayloadSchemaType
    print(f"PayloadSchemaType available: {[x for x in dir(PayloadSchemaType) if not x.startswith('_')]}")
except ImportError:
    print("PayloadSchemaType not available")

# Try creating a text index on a test basis
print("\n--- Attempting text index on title field ---")
try:
    client.create_payload_index(
        collection_name=COLLECTION_NAME,
        field_name="title",
        field_schema="text",
    )
    print("Text index created on 'title' successfully!")
except Exception as e:
    print(f"Create text index failed: {type(e).__name__}: {e}")

# Check existing indexes
print("\n--- Existing indexes ---")
try:
    # Try to list payload indexes
    from qdrant_client.http import models as rest_models
    # Use the local client's internal API
    collection_info = client.get_collection(COLLECTION_NAME)
    print(f"Collection config: {collection_info.config}")
except Exception as e:
    print(f"List indexes failed: {e}")
