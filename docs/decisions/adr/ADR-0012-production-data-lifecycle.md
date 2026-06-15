---
ADR: ADR-0012
title: "ADR-0012: 区分演示重置与生产部署的数据生命周期"
status: Accepted
implementation: Partial
date: 2026-05-29
implemented: 2026-06-08
deciders: 维护者（2026-05-29 Accept；schema 管理推荐 Flyway/Liquibase，最终选型留 Implementation）
---

# ADR-0012: 区分演示重置与生产部署的数据生命周期

> **前瞻提案**。方向已由维护者于 2026-05-29 确认（OQ-OPS-1）：当前删卷为**演示重置**（符合预期）；**生产端将一并去除「删卷」与「插入 seed」流程**。本 ADR 形式化两套数据生命周期，并提出生产端 schema 演进机制。

## 状态（Status）

**Decision: Accepted. Implementation: Partial（2026-06-10 复核）。**

实现选型：**Flyway**（Spring Boot 3.2.5 内置 Flyway 9.22.3）。
- `backend/src/main/resources/db/migration/V1__initial_baseline.sql`：以 `01_create_schema.sql` 为基线快照。
- `spring.flyway.baseline-on-migrate=true` + `baseline-version=1`：演示/initdb 场景自动基线化，不重建 schema；空库场景 V1 正常建 schema。
- `docker-compose.prod.yml`：生产 compose override，使用 `db/Dockerfile.prod`（无 initdb 脚本）。
- `Jenkinsfile`：保留演示 CI 删卷重建路径，注释说明生产部署命令。
- `FlywayMigrationTest`：用独立 Testcontainers 容器（无 initdb）验证 V1 在空库正常执行。

> **实施勘误（2026-06-10）**：Flyway 与生产 DB override 已落地，但合并后的 production compose 仍启动 `data-loader`，且未等待 Flyway 完成；loader 主要读取 CSV 快照，并非本文原先描述的完整 PG 派生流程。因此“生产数据生命周期”只能标记为 Partial，待 loader 顺序、数据来源、敏感字段最小化与生产验证完成后再转 Complete。

## 背景（Context）

✅ CI（`Jenkinsfile:20`）执行 `docker compose down --remove-orphans --volumes --rmi local` → `up -d --build`：每次部署**清空** `postgres_data`/`neo4j_data` 卷。
✅ 数据库初始化依赖 `db/initdb/*.sql`：建表（`01`）+ 字典（`02`/`03`）+ 大量种子（`21..46`、`88`）+ 序列同步（`99`）。PostgreSQL **仅在数据目录为空时**执行 initdb。
⚠️ Neo4j data-loader 一次性运行（`restart:"no"`），但当前主要读取 CSV 快照；仅 users 另有 PG 导入脚本，且无可靠增量同步（OQ-DATA-1）。
> 事实后果：在保留数据的环境运行当前 CI 将**丢失全部数据**（current-state-assessment 列为高风险）。

## 决策（Decision）

将**演示/CI 重置**与**生产部署**拆分为两条独立的数据生命周期：

- **演示/CI（保留现状）**：可继续 `down --volumes` + initdb 种子，用于每次干净重建。
- **生产部署（新增）**：
  1. **不**执行 `down --volumes`（卷持久化）；
  2. **不**执行种子脚本（`21..46`、`88` 视为开发夹具，仅限 dev/demo）；
  3. 采用**前向式 schema 迁移**机制管理结构演进（不丢数据）。

生产 schema 管理（待选）：
- **A) 引入 Flyway/Liquibase**：以现有 `01_create_schema.sql` 为基线（baseline），后续以版本化迁移演进。**（推荐）**
- **B) 保留 initdb + 环境开关跳过 seed + 永不删卷**：改动最小，但 initdb 只在空目录运行，**无法**承担结构演进，长期不足。

**迁移撰写工作流（2026-06-08 细化，维护者定）**：**DBML（dbdiagram.io）保持为 schema 的单一设计真相**；baseline `V1` = 当前 schema 快照（已完成）。**`V2` 起的迁移不手写**——用 **schema-diff 工具**（如 Atlas / Liquibase diff / Postgres `migra`）对比「DBML 导出的期望 schema vs 当前库」**自动生成** Flyway 迁移，人工评审后入 `backend/src/main/resources/db/migration/`。即 **DBML 为源、迁移为派生**，在获得 migration 好处的同时保住既有 DBML 工作流；ERD（`.mmd`）由 schema 重新派生、不手工编辑。后续改 schema 的 ADR（0013 / 0010 / 0018）均按此流程撰写迁移。

## 后果（Consequences）

- **正面**：消除"CI 删卷导致生产数据丢失"的高风险；获得安全的结构演进路径；种子退化为开发夹具。
- **负面 / 代价**：
  - 需建立迁移基线与流水线分离（dev/demo vs prod）。
  - 需配套**备份/恢复与回滚**策略（OQ-OPS-4 当前缺失）。
  - Neo4j 派生视图的生产**再同步/增量**策略需一并设计（关联 OQ-DATA-1，可另立 ADR）。
- **影响范围**：`Jenkinsfile`/CI、`db/initdb/`、`docker-compose.yml`、`operations/` 文档。

## 考虑过的备选（Alternatives Considered）

- **生产沿用当前 CI（删卷 + 种子）** — 否决（维护者）：等同每次清库，生产不可接受。
- **手工 SQL 改库** — 否决：不可重复、不可审计、易出错。

## 关联（References）

- [operations/deployment.md](../../operations/deployment.md)、[operations/runbook.md](../../operations/runbook.md)、[data-architecture.md §3](../../architecture/data-architecture.md)、[open-questions.md](../../modernization/open-questions.md)（OQ-OPS-1，关联 OQ-OPS-4 / OQ-DATA-1）。
- 代码：`Jenkinsfile:17-24`、`db/initdb/*.sql`、`docker-compose.yml`。
