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
| [ADR-0007](ADR-0007-jwt-stateless-auth.md) | JWT 无状态鉴权 | Accepted (Retrospective)，⚠️ 当前停用 | 见安全文档 |

> 这些回溯性 ADR 是对现状的**逆向描述**，其中无法从代码证明的理由均已标注。任何要**改变**这些决策的提案，应新增更高编号的 ADR。
