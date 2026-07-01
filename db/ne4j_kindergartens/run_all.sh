#!/bin/bash
set -euo pipefail

echo "=== Neo4j 派生图 sync 시작 (source: PostgreSQL) ==="

# 1) 防御层：清除历史节点上可能残留的 S0/PII 属性（幂等）。新 loader 从源头白名单
#    SELECT、不投影 PII，此步是 INC-003 的纵深防御。
python no000_scrub_sensitive.py

# 2) 长生命周期增量 sync：
#    - bootstrap（空图/无水位）→ DETACH DELETE 全量重建 + 初始化各表水位
#    - 稳态 → while 循环：每 tick 以水位增量 upsert + 周期对账清孤儿（前台进程，不退出）
#    容器 restart: unless-stopped，进程随容器长驻。
exec python load_graph.py
