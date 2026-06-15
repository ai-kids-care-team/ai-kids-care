# 现状评估（Current State Assessment）

> ⚠️ **历史快照，不再代表当前状态。** 本文主体基线为 2026-05-29，附录复核为 2026-06-07；其中“无测试”等结论已在 2026-06-08 后失效。最新全仓审计见 [`docs/assessments/2026-06-10-codebase-audit.md`](../assessments/2026-06-10-codebase-audit.md)。
>
> 对系统当前状态的**客观、事实性**评估。区分优点、技术债、风险。**不提供修复方案**（Discovery 模式）；处置由团队在 Design 模式下决定。
>
> 基线时间：**2026-05-29**；另附 **2026-06-07 独立复核记录**（见文末「独立复核记录」一节）。

## 成熟度概览

🔶 综合判断：系统处于 **可演示 / 单人维护（MVP-ish）** 阶段，具备清晰的领域模型与较完整的后端 API，但在**安全强制、AI 闭环、测试、生产运维**上尚未达到生产成熟度。

| 维度 | 状态 | 说明 |
| --- | --- | --- |
| 领域模型 | 🟢 较成熟 | 核心 28 表（+ `menu`/`common_codes` 字典 2 表，共 30）、强约束、多租户、DBML 为源 |
| 后端 API | 🟢 较完整 | 25 控制器，统一分层，OpenAPI |
| 前端 | 🟡 部分 | 覆盖核心场景，< 后端全集；静态导出 |
| AI 能力 | 🟡 实验性 | 训练/推理/实时告警齐备，但未与业务闭环 |
| 安全 | 🔴 未强制 | 鉴权关闭、默认密钥、DEBUG 日志 |
| 测试 | 🔴 缺失 | 无自动化测试 |
| 运维 | 🟡 早期 | compose 可跑；CI 删卷；可观测性弱 |
| 文档 | 🟢 改善中 | 多语 README + 本知识库 |

## 优点（值得保留）

- ✅ **清晰的分层与边界**：Controller/Service/Repository + DTO/VO 分离，符合 `CLAUDE.md` 原则。
- ✅ **数据库优先 + 代码生成**：DBML→SQL→codegen 链路一致、可重复。
- ✅ **强数据约束**：复合外键、唯一索引、`timestamptz`、统一状态枚举，schema 质量高。
- ✅ **多租户结构基础**：`kindergarten_id` 复合键从结构上防止跨租户串联。
- ✅ **合规意识**：证据文件保留期/法务保全/哈希、RRN 拆分存储（后位**单向哈希、不可逆**，见 [ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md)）、流凭证 AES-GCM、审计表。
- ✅ **AI 工程化细节**：实时告警的持续性规则（去抖）、黑屏门控、冷却、断线重连，体现实战考量。

## 技术债与缺口（事实陈述）

> 以下为**客观记录**，对应编号见 [open-questions.md](open-questions.md)。

1. **安全未强制** ✅：鉴权关闭（OQ-SEC-1，已决 → [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md)）、access/refresh 无别（OQ-SEC-2）、默认密钥（OQ-SEC-3/5）、RRN 命名/注释误导（OQ-SEC-4，已决：单向哈希，见 [ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md)）、DEBUG 日志（OQ-SEC-6）、审计落地存疑（OQ-SEC-7）、运行时租户隔离存疑（OQ-SEC-8）。
2. **AI 与业务断链** ✅：检测不落库，闭环未通（OQ-AI-1）；训练配置/数据集未文档化（OQ-AI-2）。
3. **测试覆盖仍不均衡** ✅：后端已建立 Testcontainers 集成、单元和契约测试，并接入 GitHub Actions/Jenkins；前端与 AI 仍无自动化测试（OQ-TEST-1）。
4. **运维不成熟** ✅：CI 删卷（OQ-OPS-1）、无 TLS（OQ-OPS-3）、可观测性弱、无系统告警。
5. **一致性/完整性小问题**：`relationship_enum` 取值不足（OQ-DATA-2）、`notifications` NOT NULL 过严（OQ-DATA-3）、无统一异常处理（OQ-ARCH-2）、密码重置未实现（OQ-PROD-3）、角色档案复用（OQ-PROD-4）。
6. **派生数据新鲜度** ✅：Neo4j 一次性加载，无增量同步（OQ-DATA-1）。
7. **列表 `keyword` 过滤多为空操作** ✅（2026-06-12 复核）：15 个公开列表端点暴露 `keyword` 参数，其中 **12 个静默忽略**（service 直接 `repository.findAll(pageable)` + 统一注释 `// TODO: filter X by keyword`），仅 3 个真正实现（Kindergarten/Announcement/Teacher）。属「伪实现」——Swagger 宣称可搜索但实际不过滤、返回 HTTP 200 无报错（OQ-ARCH-4）。

## 风险摘要

| 风险 | 严重度（🔶 主观） | 触发条件 |
| --- | --- | --- |
| 默认凭据/鉴权关闭进入生产 | 高 | 未覆盖默认值即上线 |
| CI 删卷导致生产数据丢失 | 高 | 在保留数据的环境跑当前 CI |
| 无测试导致回归 | 中–高 | 任意改动 |
| 儿童 PII 无 TLS / RRN 处理不当 | 高（合规） | 公网传输 / 监管审查 |
| AI 闭环缺失被误认为已具备 | 中 | 误把种子数据当线上检测 |

## 与使命对齐（`CLAUDE.md`）

系统定位为"长期生产系统"。当前状态在**可维护性**（分层、codegen、强 schema）上基础良好，但在**业务正确性的端到端保障**（安全强制、AI 闭环、测试）上存在明显缺口。本评估旨在"改善理解"，为后续在 Design 模式下、以 ADR 形式推进改进提供事实依据。

> 下一步的工作**登记**（非方案）见 [roadmap.md](roadmap.md)。

## 独立复核记录（2026-06-07）

> 由接手人以「资深架构师」视角对本评估做**独立的代码级复核**（逐条拉到源码/配置核对，而非复述文档）。仍遵循 Discovery 模式：只记录事实，不在此给方案。

**方法**：将 2026-05-29 评估与各 ADR 的关键论断逐条对照源码——鉴权、默认密钥、CI 删卷、AI 解耦、表数量、全局异常处理、测试缺失、占位端点等。

**总体结论** ✅：**文档与代码漂移极低，知识库可信**。下列论断均经直接验证成立：

- 鉴权关闭：`SecurityConfig.java:48` `/api/v1/**` 为 `permitAll()` + `:51` JWT 过滤器注释。
- 默认密钥：`application.yml:27` JWT secret 默认值；compose 中 `kids_pass` / `rose100!`。
- DEBUG 日志：`application.yml:32` `root: DEBUG`。
- CI 删卷：`Jenkinsfile` `docker compose down --remove-orphans --volumes --rmi local`。
- AI 解耦：整个 `ai/` 目录唯一的 `requests` 引用在 `utils/pushover.py`（调外部告警 API），无任何 DB / 后端 HTTP 调用——「检测→落库」物理上不存在（佐证 [OQ-AI-1](open-questions.md)）。
- 表数量：`01_create_schema.sql` 精确 **28 张业务表**（+ `menu`/`common_codes` = 30）。
- 无全局异常处理：后端**零** `@ControllerAdvice`/`@ExceptionHandler`，service 抛 `IllegalArgumentException`/`EntityNotFoundException`（佐证 [OQ-ARCH-2](open-questions.md)）。
- 测试缺失：`backend/src/test/` 为空目录（佐证 [OQ-TEST-1](open-questions.md)）。

**本次新增的两项事实**（此前知识库未单列）：

1. **列表 `keyword` 过滤多为静默空操作** → 已登记 [OQ-ARCH-4](open-questions.md)（含 17/14/3 精确分布与影响分析）。
2. **注释级文档漂移（低）**：`frontend/src/config/api.ts:3` 注释称「백엔드 디버그 포트(8081)」，但同文件默认值为 `http://localhost:8080/api/v1`（端口 8080）。属无害但易误导的注释陈旧，建议 Implementation 时顺手勘误；无需团队决策，故不单列 OQ。
