#!/bin/bash
set -euo pipefail

echo "=== Neo4j 派生图重建 시작 (source: PostgreSQL) ==="

# 1) 防御层：清除历史节点上可能残留的 S0/PII 属性（幂等）。新 ETL 从源头白名单
#    SELECT、不投影 PII，此步是 INC-003 的纵深防御。
python no000_scrub_sensitive.py

# 2) 一次性 PG → Neo4j ETL：清空旧图并按非 PII 白名单从 PostgreSQL 重建。
python load_graph.py

echo "=== Neo4j 派生图重建 완료 ==="
