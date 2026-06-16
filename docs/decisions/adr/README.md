# ADR 索引（Architecture Decision Records）

本目录记录 AI Kids Care 的架构决策。约定与流程见 [../README.md](../README.md)。

## 状态图例

| 状态 | 含义 |
| --- | --- |
| `Proposed` | 已提出，待评审 |
| `Accepted` | 已采纳为目标约束；不代表代码已经落地 |
| `Accepted (Retrospective)` | **回溯记录**：决策早已固化在代码中，此处为逆向补记现状（非新提案） |
| `Rejected` | 评审后否决 |
| `Superseded` | 被更新的 ADR 取代 |

## 索引

| ID | 标题 | 状态 | 说明 |
| --- | --- | --- | --- |
| [ADR-0000](ADR-0000-record-architecture-decisions.md) | 采用架构决策记录（ADR） | Accepted | 流程奠基 |
| [ADR-0001](ADR-0001-polyglot-monorepo.md) | 采用多语言 Monorepo | Accepted (Retrospective) | frontend/backend/ai/db 同仓 |
| [ADR-0002](ADR-0002-dual-datastore-postgres-neo4j.md) | PostgreSQL + Neo4j 双存储 | Accepted (Retrospective) | 关系库为可信源、图库为派生视图（"SoR≠后端唯一写入者"，2026-06-07 勘误） |
| [ADR-0003](ADR-0003-multitenancy-kindergarten-id.md) | 以 kindergarten_id 做多租户 | Accepted (Retrospective) | 复合键租户隔离 |
| [ADR-0004](ADR-0004-layered-backend-codegen.md) | 分层后端 + 代码生成（DB-first） | Accepted (Retrospective) | Controller/Service/Repository + codegen |
| [ADR-0005](ADR-0005-frontend-static-export.md) | 前端静态导出 + Nginx | Accepted (Retrospective) | Next.js `output: export` |
| [ADR-0006](ADR-0006-decoupled-ai-videomae.md) | 解耦的 VideoMAE AI 服务 | Accepted (Retrospective)，⚠️ 数据集成部分 Superseded by ADR-0015 | 技术/部署解耦有效；"AI 不连库"系临时态误记，已勘误（2026-06-07） |
| [ADR-0007](ADR-0007-jwt-stateless-auth.md) | JWT 无状态鉴权 | **Superseded by ADR-0016**（2026-06-07） | 会话机制改为服务端 session（ADR-0016）；鉴权恢复见 ADR-0009 |
| [ADR-0008](ADR-0008-language-governance.md) | Language Governance（语言治理） | Accepted | 单一 SoT + 受控词表 + i18n 轨道 + 多 Agent |
| [ADR-0009](ADR-0009-restore-auth-enforcement.md) | 恢复后端鉴权强制 | **Accepted (2026-05-29)** | 第一轮重构后落地（OQ-SEC-1） |
| [ADR-0010](ADR-0010-rrn-one-way-hash.md) | 主民登录号（RRN）单向哈希 | **Accepted (2026-05-29)** | 哈希算法选定 **HMAC-SHA-256 + pepper**；schema/ERD 勘误与数据迁移留 Implementation（OQ-SEC-4） |
| [ADR-0011](ADR-0011-extract-codegen-subproject.md) | 抽离 pg-spring-crud-codegen 为独立工程 | **Accepted (2026-05-29)，✅ Implemented (2026-05-29)** | 方案 A 仓内迁址 + 软指针完成；后续可 `git filter-repo` 带史拆仓（OQ-ARCH-3） |
| [ADR-0012](ADR-0012-production-data-lifecycle.md) | 演示重置 vs 生产数据生命周期 | **Accepted / Partial** | Flyway 与 prod override 已落地；loader 顺序与 CSV 快照问题仍阻碍生产就绪 |
| [ADR-0013](ADR-0013-dictionary-tables-governance.md) | menu/common_codes 字典表治理 | **Accepted (2026-05-29)** | `menu` → C 静态；`common_codes` → β 后端枚举元数据端点 + 前端 i18n（OQ-DATA-4） |
| [ADR-0014](ADR-0014-test-baseline.md) | 建立测试基线（Test Baseline） | **Accepted / Complete** | 后端 Testcontainers 基线已提交；本地运行仍要求可用 Docker engine |
| [ADR-0015](ADR-0015-ai-detection-closed-loop.md) | AI 检测闭环集成契约 | **Accepted (2026-06-07)** | 终态必做、排加固轨之后；**V1：AI 直写 PG + 后端 LISTEN/NOTIFY**；通知复核后发家长；勘误 ADR-0002/0006 |
| [ADR-0016](ADR-0016-server-side-session-auth.md) | 服务端会话鉴权（替代 JWT） | **Accepted (2026-06-07)** | Spring Session + Redis + httpOnly cookie；**取代 ADR-0007**；排在 ADR-0009 前；实现委派独立 session |
| [ADR-0017](ADR-0017-tls-https-termination.md) | TLS/HTTPS 终结与强制 | **Accepted (2026-06-07)** | 由 ADR-0016 的 `Secure` cookie **硬触发**；边缘反代终结 + HTTP→HTTPS + HSTS；决断 OQ-OPS-3；实现委派独立 session |
| [ADR-0018](ADR-0018-notification-subsystem.md) | 通知子系统（后端拥有、复核门控） | **Accepted (2026-06-07)** | 后端发、**家长复核后**通知、Pushover 渠道；决断 OQ-DATA-3；移除 AI 演示告警；实现委派独立 session |
| [ADR-0019](ADR-0019-effective-authorization-context-tenant-enforcement.md) | 服务端有效授权上下文与租户强制边界 | **Accepted (2026-06-14)** | 每请求权威解析、集中 policy、tenant-aware repository、平台 tenant context 与 session 吊销 |
| [ADR-0020](ADR-0020-branch-protection-release-model.md) | 两层分支模型与发布门（develop trunk + main 发布） | **Accepted (2026-06-15)** | `develop`=主 agent 集成 trunk（可直推）；`main`=人工发布门（PR + code-owner + CI 必过 + fresh review） |
| [ADR-0021](ADR-0021-admin-audit-schema-migration.md) | Admin 审批与安全审计的 schema 迁移 | **Accepted (2026-06-15)** | 一次 V2（DBML-first）：新增 `REJECTED`；role/membership 单 ACTIVE 唯一约束 + scope CHECK；`audit_logs` 加 `scope_type`/可空/CHECK + `effective_role`/`result`/`correlation_id` |
| [ADR-0022](ADR-0022-cd-github-actions-ghcr-watchtower.md) | CD 改用 GitHub Actions + GHCR + watchtower，退役 Jenkins | **Accepted / Implemented (2026-06-16)** | release tag 触发构建→冒烟→推 GHCR 私有 `:<版本>`+`:prod`→演示机 watchtower 自动部署；develop 只 CI；AI 不纳入初版；演示数据持久。`v0.1.0` 端到端首发已验证；OQ-2/3/4 运营级后续 |

> ADR-0000–0008 为**回溯性**记录（对现状的逆向描述）。**ADR-0009/0010/0011/0012/0013 于 2026-05-29 由维护者 Accept**，落地待 Implementation 模式。**ADR-0014 / 0015 / 0016 于 2026-06-07 由维护者 Accept**（均实现委派独立 Implementation session）：0015 = V1「AI 直写 PG」并**勘误 ADR-0002/0006**；**0016 = 服务端会话鉴权，Supersedes ADR-0007**（取代无状态 JWT，排在 ADR-0009 前）。**ADR-0017（TLS/HTTPS，由 0016 硬触发）/ 0018（通知子系统）于 2026-06-07 由维护者 Accept，实现委派独立 session。ADR-0019 于 2026-06-14 由维护者 Accept，固化 Effective Authorization Context 与 tenant enforcement 的实现边界。** 详见 [roadmap.md](../../modernization/roadmap.md) 的修订次序。任何要**改变**既有决策的提案，应新增更高编号的 ADR——本次 ADR-0016 即范例（取代 ADR-0007，原 ADR 仅加 `Superseded by` 状态指针、正文不改）。
