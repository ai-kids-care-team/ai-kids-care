import os

PG_HOST = os.getenv("DB_HOST", "localhost")
PG_PORT = int(os.getenv("DB_PORT", "5432"))
PG_NAME = os.getenv("DB_NAME", "kids_postgres_db")
PG_USER = os.getenv("DB_USER", "kids_user")
PG_PASSWORD = os.getenv("DB_PASSWORD", "kids_pass")

NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "math1106")

BATCH_SIZE = int(os.getenv("BATCH_SIZE", "500"))

