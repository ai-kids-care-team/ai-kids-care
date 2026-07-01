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

# ── 增量 sync（长生命周期 loader）────────────────────────────────────────────
# POLL_INTERVAL_SEC : 每个增量 tick 之间的 sleep 秒数（稳态延迟上界）。默认 30s，
#                     对齐 AI supervisor STREAM_POLL_INTERVAL_SEC。
# RECONCILE_EVERY   : 每多少个 tick 做一次全量 id 对账清孤儿。默认 1（每 tick 对账，
#                     图极小、对账极廉价）。
POLL_INTERVAL_SEC = int(os.getenv("POLL_INTERVAL_SEC", "30"))
RECONCILE_EVERY = int(os.getenv("RECONCILE_EVERY", "1"))

