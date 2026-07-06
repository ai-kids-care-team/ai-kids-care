from neo4j_connect import driver

# 独立 tool 也从 load_graph 复用同一份白名单强制逻辑（NODE_ALLOWED_PROPS 派生自 loader 的
# 白名单列），因此不再有第二份字段清单需要维护。
from load_graph import NODE_ALLOWED_PROPS, enforce_node_prop_whitelist

# ============================================================
# INC-003 敏感属性清除脚本（白名单强制 / whitelist）
# ------------------------------------------------------------
# 目的:
#   把 Neo4j 派生图上任何**未在 loader 白名单内**的节点属性 REMOVE 掉，使图回到洁净状态。
#   这覆盖历史 loader 版本残留的 S0/PII（如旧版误投的 contact_name、address、rrn_* 等），
#   MERGE+SET 只覆盖不删除，故清除必须显式做。
#
#   从「黑名单」（列举要删的字段）改为「白名单」（保留允许集、strip 其余）——根治黑名单漂移：
#   旧脚本既漏了真被投的 contact_name，又留着 loader 从不投的死字段 rrn_encrypted
#   （PG 实列名是 rrn_hash，且三实体白名单根本不投 rrn），证明黑名单必然与投影脱节。
#
# 与 load_graph.py 内联 guard 的关系:
#   load_graph.py 现在在 bootstrap 后 + **每个增量 tick** 都调用 enforce_node_prop_whitelist，
#   那才是稳态主防线。本脚本保留为**可独立运行的一次性 tool**（run_all.sh 中 loader 启动前先跑
#   一遍，快速清洗历史脏数据），已不再是唯一防线。
#
# 幂等性:
#   已合规（无越权属性）时 REMOVE 不执行，无副作用，可反复运行。
# ============================================================


if __name__ == "__main__":
    with driver.session() as session:
        total = enforce_node_prop_whitelist(session)
        for label, allowed in NODE_ALLOWED_PROPS.items():
            print(f"{label}: 保留白名单 {sorted(allowed)}")
        print(f"INC-003 白名单强制完成（本次清除越权属性 {total} 项，0 表示图已洁净）")
