---
type: assessment
date: 2026-06-10
status: Superseded
superseded_by: 2026-06-17-followup-audit.md
baseline_commit: ead603e
scope: monorepo implementation, architecture, documentation, tests, and deployment
---

# AI Kids Care 代码库审计

> **[已废弃]** 本审计已被 [2026-06-17-followup-audit.md](2026-06-17-followup-audit.md) 取代（superseded 2026-06-18）。以下内容仅作历史参考。

## 执行摘要

AI Kids Care 已有较广的领域模型与清晰可辨的 monorepo 结构，但尚未达到生产级安全管理平台的成熟度。当前更接近：面向演示的 CRUD 系统、部分前端流程、独立 AI 实验，以及声明的目标架构与运行时强制行为之间的明显缺口。

最紧急的问题不是界面完善度。全部业务 API 可匿名访问，tenant 边界没有强制，多类 API response 暴露密码或 RRN hash。AI 检测→复核→通知的闭环尚未连接。production compose 与 Neo4j 数据模型也必须修正后，才能被视为生产资产。

## 当前实现架构（As-Built）

| 区域 | 当前实现 |
| --- | --- |
| Frontend | Next.js 16 static export，17 个 page 文件，Nginx `/api` proxy，Redux + 两套独立 HTTP client |
| Backend | Spring Boot 3.2.5，25 个 Controller，生成器风格的 Controller/Service/Repository/DTO/VO 分层 |
| 关系数据 | PostgreSQL 16，28 张核心表 + `menu`/`common_codes`，JPA `ddl-auto=validate`，Flyway V1 |
| 图数据 | Backend 查询 Neo4j 5.19；loader 主要基于 CSV，不是可靠的 PostgreSQL projection |
| AI | FastAPI `/health`、`/predict/path`、`/predict/upload`；realtime script 写 CSV 并直接告警 |
| Deployment | Root compose 编排 frontend/backend/PostgreSQL/Neo4j/loader；AI 使用独立 compose |
| Tests | 4 个 backend test 文件使用 Testcontainers；frontend/AI 无测试 |

## 端到端流程状态

| 流程 | 状态 | 观察 |
| --- | --- | --- |
| Login | Partial | Login/refresh 返回 token，但 backend authentication 已关闭，access/refresh token 等价 |
| Authorization | Not operational | `/api/v1/**` 为 `permitAll`，JWT filter 未注册，也没有 method-level authorization |
| Tenant isolation | Not operational | 结构上有 `kindergarten_id`，service 未从会话推导或强制 tenant scope |
| CCTV management | Partial | CRUD 与 UI 存在；访问控制和生产凭据管理不完整 |
| AI detection | Experimental | 文件推理与 stream detection 存在；不写业务数据库 |
| Event review | Broken contract | Frontend 发送 snake_case，backend DTO 期待 camelCase；review 与 event update 是两个非原子请求 |
| Notification | Prototype | Backend Pushover 调用传空 credential；AI script 固定收件人并绕过业务规则 |
| PostgreSQL → Neo4j | Snapshot import | 大多数 node/relationship 来自提交的 CSV；只有一个 users 脚本读取 PG |
| Production deployment | Partial | Flyway 已存在，但 production compose 仍启动 one-shot loader，且 migration/loader 存在启动竞态 |

## 优先级发现

| ID | Priority | Finding | Impact |
| --- | --- | --- | --- |
| SEC-001 | P0 | 全部业务 API 匿名开放；role 与 tenant 模型未强制 | 可跨园读写删除儿童和员工数据 |
| SEC-002 | P0 | `UserVO` 返回 `passwordHash`；child/guardian/teacher VO 返回 RRN hash；CRUD DTO 允许直接写这些存储值 | Credential/PII 暴露、hash 替换与离线攻击面 |
| SEC-003 | P0 | JWT/DB/Neo4j/camera encryption 存在默认值；AI realtime 告警收件人写死 | 不安全生产默认值与 secret/routing 泄漏 |
| FLOW-001 | P0 | Event review request 字段与 backend DTO 不匹配；status/review 写入无事务 | 核心安全复核流程失败或状态不一致 |
| DATA-001 | P0 | Neo4j 保存图查询不需要的密码 hash、RRN、地址、电话和邮箱 | 无必要扩大敏感数据存储与泄漏范围 |
| OPS-001 | P0 | Production compose 在 Flyway 旁启动 CSV/PG loader，且未保证 migration 后执行 | 首次部署有竞态，Neo4j 可能陈旧或来自快照 |
| AUTH-001 | P1 | 密码重置/验证码 frontend path 与 backend 不一致；多个 backend endpoint 为占位 | 用户账户流程不可用 |
| API-001 | P1 | Controller 不使用 `@Valid`；无全局 error contract；部分 DTO constraint 与字段类型不兼容 | 非法输入进入 persistence，常见错误返回 500 |
| FE-001 | P1 | `ProtectedRoute` 未使用；localStorage 被当作 session authority；园所 scope 可从 demo user ID 区间推断 | UI 权限可绕过，tenant context 不可靠 |
| GRAPH-001 | P1 | Graph loader 混用 CSV snapshot 与单个 PG users import；graph query 假设只有一行结果 | 与 PG 漂移，多历史关系时可能查询失败 |
| CI-001 | P1 | Jenkins 只 gate backend test；frontend lint 失败；frontend/AI 无测试 | 损坏的 frontend/API contract 可进入部署 |
| DB-001 | P1 | DBML、initdb schema SQL、Flyway V1 独立维护且无 CI 等价校验 | Demo、production 与设计 schema 可分叉 |
| AI-001 | P1 | AI config YAML 为空；运行行为硬编码；无 persistence contract 或测试 | 模型行为不可复现，也难以安全集成 |
| DOC-001 | P1 | 文档混合当前事实、未来意图和历史快照 | Agent/维护者可能把计划误认为已实现 |
| API-002 | P2 | 14 个列表 endpoint 静默忽略 `keyword`；部分生成 CRUD 暴露内部字段 | API contract 误导，生成默认值不安全 |

## 文档可信度评估

| 文档组 | 可信度 | 评估 |
| --- | --- | --- |
| Build file、Controller、entity、migration、compose | 当前事实 High | 直接实现证据，但仍需 runtime 验证 |
| ADR-0001 至 ADR-0008 | Medium | 有用的历史解释；部分理由为推断，ADR-0006 已发生勘误 |
| ADR-0009 以后 | 意图 High，实施状态 Low | Accepted 决策是有效方向，不是交付证明 |
| `docs/architecture/` | Medium | 大体拓扑有用；Neo4j 来源、CI、测试声明发生漂移 |
| `docs/api/` | Medium-low | Endpoint 目录有用，但手工 prose 未捕获前后端 payload 不匹配 |
| `docs/modernization/current-state-assessment.md` | 作为当前状态 Low | 2026-06-07 的测试结论在 2026-06-08 后已过期 |
| `docs/modernization/roadmap.md` | Medium | 比 ADR index 更接近实施跟踪，但完成状态仍需代码核验 |
| Root 多语言 README | Medium | Onboarding 较好；人工维护多份翻译会增加漂移 |

## 文档编排问题

1. `docs/modernization/` 混合不可变 assessment、live question 与交付 planning。
2. Architecture 文档同时包含 as-built facts 与未来 ADR outcome。
3. ADR status 同时表示“决策已接受”和“代码已完成”。
4. API contract 依赖手工摘要，未由 OpenAPI 生成并校验 client。
5. Database truth 分散在 DBML、initdb SQL、Flyway、JPA、seed SQL 和 Neo4j CSV snapshot。
6. 数据库文档分布在 `db/**` 与 `docs/db/**`，但没有清晰 ownership 规则。
7. `db/ne4j_kindergartens/` 及多个 script 名拼写错误，影响检索与自动化。
8. Root `pg-spring-crud-codegen/` 加 `scripts/codegen/` pointer 保留了迁移历史，但增加导航噪音。（**注：`pg-spring-crud-codegen/` 与 `scripts/codegen/` 已于 2026-06-18 由 ADR-0027 删除**）
9. 审计开始时 `.ai/CONTEXT.md` 已存在，但 `AGENTS.md`/`CLAUDE.md` 仍指向 `.agents/CONTEXT.md`；本次已修正入口指针。

## 建议顺序

1. **先收敛暴露面**：从 public contract 移除敏感字段，禁用不安全 generic CRUD，移除写死收件人，明确 production secret 要求。
2. **建立身份边界**：把 ADR-0016/0017/0009 与 server-derived tenant scope、authorization test 一起落地。
3. **修复 contract baseline**：增加 validation/error envelope，修复前后端 path/payload casing，把 event review 收敛为一个 backend transaction。
4. **让 CI 反映真实质量**：要求 backend test、frontend lint/build、API contract check、AI test/syntax 与 compose validation。
5. **规范数据所有权**：把 Neo4j 重做为最小派生 projection，移除 PII，定义 refresh/rebuild。
6. **从 Approved Spec 完成 AI 闭环**：detection persistence、evidence lifecycle、human review 与 notification policy。
7. **消除重复真相**：生成 OpenAPI artifact，并强制 DBML/migration/initdb 一致。

## 验证记录

| 检查 | 结果 | 限制 |
| --- | --- | --- |
| `npm.cmd ci --legacy-peer-deps` | Passed | 报告 3 moderate + 5 high dependency advisory，尚未逐项分诊 |
| Clean `npm.cmd run build` | Passed | Static export 生成 20 个 route（含 framework route） |
| `npm.cmd run lint` | Failed | 22 errors / 25 warnings |
| `.\gradlew.bat test` | Blocked | Testcontainers 找不到运行中的 Docker engine，未执行 assertion |
| Python AST parse | Passed | 解析 30 个 Python 文件，不代表行为测试 |
| Production compose merge | Parsed | 确认 production override 仍保留 `data-loader` |

## 开放问题

- 是否存在仓库外 PRD、threat model、privacy impact assessment 或韩国监管审查材料？
- 哪些角色可以查看 live CCTV 与 detection evidence，适用什么 guardian/teacher consent 模型？
- Neo4j 是否仍提供必要产品价值，还是可由 PostgreSQL 提供儿童关系视图？
- 仓库中的 CSV 是否全部为 synthetic data，来源是否有记录？
- 目标 production 环境、域名、证书 owner、backup target 与 incident response owner 分别是谁？
