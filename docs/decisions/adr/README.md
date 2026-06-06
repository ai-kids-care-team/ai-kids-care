# ADR 索引（Architecture Decision Records）

本目录记录 AI Kids Care 的架构决策。约定与流程见 [../README.md](../README.md)。

## 状态图例

| 状态 | 含义 |
| --- | --- |
| `Proposed` | 已提出，待评审 |
| `Accepted` | 已采纳并生效 |
| `Accepted (Retrospective)` | **回溯记录**：决策早已固化在代码中，此处为逆向补记现状（非新提案） |
| `Rejected` | 评审后否决 |
| `Superseded` | 被更新的 ADR 取代 |

## 索引

| ID | 标题 | 状态 | 说明 |
| --- | --- | --- | --- |
| [ADR-0000](ADR-0000-record-architecture-decisions.md) | 采用架构决策记录（ADR） | Accepted | 流程奠基 |
| [ADR-0001](ADR-0001-polyglot-monorepo.md) | 采用多语言 Monorepo | Accepted (Retrospective) | frontend/backend/ai/db 同仓 |
| [ADR-0002](ADR-0002-dual-datastore-postgres-neo4j.md) | PostgreSQL + Neo4j 双存储 | Accepted (Retrospective) | 关系库为可信源，图库为派生视图 |
| [ADR-0003](ADR-0003-multitenancy-kindergarten-id.md) | 以 kindergarten_id 做多租户 | Accepted (Retrospective) | 复合键租户隔离 |
| [ADR-0004](ADR-0004-layered-backend-codegen.md) | 分层后端 + 代码生成（DB-first） | Accepted (Retrospective) | Controller/Service/Repository + codegen |
| [ADR-0005](ADR-0005-frontend-static-export.md) | 前端静态导出 + Nginx | Accepted (Retrospective) | Next.js `output: export` |
| [ADR-0006](ADR-0006-decoupled-ai-videomae.md) | 解耦的 VideoMAE AI 服务 | Accepted (Retrospective) | AI 独立子系统 |
| [ADR-0007](ADR-0007-jwt-stateless-auth.md) | JWT 无状态鉴权 | Accepted (Retrospective)，⚠️ 当前停用 | 见安全文档；**恢复决策已 Accept：ADR-0009（落地待 Implementation）** |
| [ADR-0008](ADR-0008-language-governance.md) | Language Governance（语言治理） | Accepted | 单一 SoT + 受控词表 + i18n 轨道 + 多 Agent |
| [ADR-0009](ADR-0009-restore-auth-enforcement.md) | 恢复后端鉴权强制 | **Accepted (2026-05-29)** | 第一轮重构后落地（OQ-SEC-1） |
| [ADR-0010](ADR-0010-rrn-one-way-hash.md) | 主民登录号（RRN）单向哈希 | **Accepted (2026-05-29)** | 哈希算法选定 **HMAC-SHA-256 + pepper**；schema/ERD 勘误与数据迁移留 Implementation（OQ-SEC-4） |
| [ADR-0011](ADR-0011-extract-codegen-subproject.md) | 抽离 pg-spring-crud-codegen 为独立工程 | **Accepted (2026-05-29)，✅ Implemented (2026-05-29)** | 方案 A 仓内迁址 + 软指针完成；后续可 `git filter-repo` 带史拆仓（OQ-ARCH-3） |
| [ADR-0012](ADR-0012-production-data-lifecycle.md) | 演示重置 vs 生产数据生命周期 | **Accepted (2026-05-29)** | 去删卷/去 seed + Flyway/Liquibase 迁移（OQ-OPS-1） |
| [ADR-0013](ADR-0013-dictionary-tables-governance.md) | menu/common_codes 字典表治理 | **Accepted (2026-05-29)** | `menu` → C 静态；`common_codes` → β 后端枚举元数据端点 + 前端 i18n（OQ-DATA-4） |

> ADR-0000–0008 为**回溯性**记录（对现状的逆向描述）。**ADR-0009/0010/0011/0012/0013 于 2026-05-29 由维护者 Accept**，落地待 Implementation 模式。任何要**改变**既有决策的提案，应新增更高编号的 ADR。
