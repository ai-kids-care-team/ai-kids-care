# 开放问题登记册（Open Questions）

> 本登记册汇总**仅凭代码无法判定、需要团队（尤其原作者）确认**的事项。每项给出：证据、为何重要、当前观察。**这里不给答案、不给方案**——只把问题摆清楚（Discovery 模式）。

约定：编号 `OQ-<域>-<序号>`，被全库文档交叉引用。域：SEC（安全）、OPS（运维）、AI（AI/集成）、DATA（数据）、PROD（产品）、ARCH（架构）、TEST（测试）、LANG（语言治理）。

---

## 安全（SEC）

### OQ-SEC-1 ｜后端鉴权当前关闭，是临时还是疏漏？
- **证据** ✅：`SecurityConfig.java` 中 `/api/v1/**` 为 `permitAll()`，且 `addFilterBefore(jwtAuthFilter, ...)` 被注释。
- **为何重要**：全部业务 API 对无凭证请求开放；角色/多租户模型未被强制执行。
- **观察**：前端实现了完整 JWT/refresh 且注释提到遇到过 401 → 🔶 推断鉴权曾开启、当前为开发/演示临时放开。需确认目标态与开启时机。
- **结论（2026-05-29，团队确认）** ✅：`permitAll` + 过滤器注释为**临时演示态**；计划在**第一轮重构完成后恢复**鉴权强制。→ 属安全态/架构决策，已 **Accept** [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md)（2026-05-29 签署；落地待 Implementation）。

### OQ-SEC-2 ｜access 与 refresh 令牌为何无区分？
- **证据** ✅：`AuthService.login()` 用同一 `generateToken(identifier)` 生成两者，无类型 claim、无独立过期。
- **为何重要**：refresh 机制不提供额外安全边界（refresh 等价于 access）。
- **观察**：是简化实现还是待完善？

### OQ-SEC-3 ｜JWT secret 默认值与 `expireSecond` 单位
- **证据** ✅：`application.yml` 有硬编码默认 secret；`jwt.expiration: 86400000` 被 `AuthService` 读为 `Integer expireSecond` 又作为 `expiresIn` 返回，而 `JwtUtil` 当毫秒用。
- **为何重要**：默认 secret 若进生产是严重风险；`expiresIn` 返回给前端的数值语义（秒？毫秒？）可能误导。
- **观察**：需确认生产 secret 注入流程与 `expiresIn` 约定。

### OQ-SEC-4 ｜RRN 应可逆加密还是单向哈希？
- **证据** ✅：schema 注释称 `rrn_encrypted` 为"암호문/암호화 저장"（密文，暗示可逆）；但 `AuthService` 用 `passwordEncoder.encode()`（BCrypt 单向）。仓库另有可逆的 `AesGcmCryptoUtil`。
- **为何重要**：若业务需展示/核验 RRN，则需可逆加密；BCrypt 无法解密。实现与注释/工具能力矛盾。
- **观察**：RRN 的权威保护方案待定。
- **结论（2026-05-29，团队确认）** ✅：RRN **采用单向哈希（不可逆）**，**不使用可逆加密**。即当前 `passwordEncoder.encode()`（BCrypt）方向正确；但列名 `rrn_encrypted` 与注释"암호문(암호화 저장)"属**误导性命名/注释**，应在后续修订（列改名/注释更正属 schema 变更，留待 Implementation）。已 **Accept** [ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md)（2026-05-29 签署；**哈希算法选定 HMAC-SHA-256 + pepper**；落地待 Implementation）。**进一步澄清（2026-05-29）**：现存所有把 RRN 描述为"可逆加密"或"암호문"的文字均为错误，须按 ADR-0010 勘误清单一并修正。

### OQ-SEC-5 ｜`.env` 内容与默认凭据
- **证据** ✅：根目录有 `.env`（89B）；compose/yml 含明文默认 `kids_pass`/`rose100!`/JWT secret。
- **为何重要**：`.env` 是否含真实密钥、是否被 git 忽略，关系到密钥泄露。
- **观察**：需核对 `.gitignore` 是否覆盖 `.env`，以及生产密钥管理方式。

### OQ-SEC-6 ｜生产日志级别
- **证据** ✅：`logging.level.root: DEBUG`。
- **为何重要**：DEBUG 在生产产生海量日志且可能泄露敏感数据。
- **观察**：是否应按环境区分日志级别？

### OQ-SEC-7 ｜审计日志是否真正落地？
- **证据** ✅：有 `audit_logs` 表与 API；❓ 未见各写操作统一写审计（无切面/拦截器）。
- **为何重要**：合规/取证依赖审计完整性。
- **观察**：审计写入是手工散落还是未实现？

### OQ-SEC-8 ｜运行时多租户隔离是否强制？
- **证据** ✅：schema 用 `kindergarten_id` 复合键提供隔离基础；❓ 未见 service 层统一注入租户过滤。
- **为何重要**：缺少运行时强制时，越权访问他园数据成为可能（叠加 OQ-SEC-1 风险更高）。
- **观察**：租户过滤策略待确认。

### OQ-SEC-9 ｜会话机制取舍：无状态 JWT vs 服务端会话（前瞻 Design）
- **背景** ✅：当前用无状态 JWT（[ADR-0007](../decisions/adr/ADR-0007-jwt-stateless-auth.md)）；项目早期曾考虑 Redis 服务端 session 后改 JWT，**改用原因未记录、现维护者不掌握**（OQ-OPS-2）。维护者 2026-06-07 提出重新评估"对本业务何者更合适、迁移成本/收益、主流方案"。
- **为何重要**：本系统是**第一方 Web 应用 + 敏感儿童 PII + 多租户 + 需即时吊销权限**（如解雇教师须立即断访问）——纯无状态 JWT 无法在过期前吊销。会话机制直接影响安全态、前端复杂度与 [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) 的落地范围。
- **观察（接手人，2026-06-07）**：宜在 **ADR-0009（鉴权恢复）落地之前**决断（此刻"恢复后"的代码尚未依赖 JWT，是切换成本最低的窗口）。两条候选均需 Redis：①**JWT-done-right**（httpOnly cookie + 短期 access + Redis 可吊销 refresh）；②**服务端 session**（Spring Session + Redis + httpOnly cookie，吊销天然、前端更简）。**待立 ADR**（若改变 ADR-0007 方向）。
- **结论（2026-06-07，维护者 Accept）** ✅：选定**服务端会话**（Spring Session + Redis + httpOnly cookie）——因客户端恒为浏览器（响应式 Web、不上原生 App）+ 需即时吊销 + 产品未上线零迁移成本。已立 [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md)（**取代 ADR-0007**），排在 [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) 之前落地。

---

## AI 与集成（AI）

### OQ-AI-1 ｜AI 检测结果为何不回流后端/数据库？
- **证据** ✅：`ai/` 全目录无 DB/后端调用；实时告警仅 Pushover/SMS + CSV。`detection_events` 等靠种子。
- **为何重要**：这是产品完整性的核心——"检测→落库→复核→展示→通知"闭环未连通。根 README 称实时链路为"实验"。
- **观察**：闭环是计划中的下一步，还是有意保持解耦？集成契约（谁写库、用何协议）待定。
- **结论（2026-05-29，团队确认）** ✅：闭环（检测→落库→复核→展示→通知）是**计划中的下一步，但当前尚未打通/连接**。集成契约（谁写库、用何协议）仍待 Design 阶段确定。

### OQ-AI-2 ｜训练配置与数据集来源
- **证据** ✅：`configs/{train,eval,infer}.yaml` 为空；训练超参在脚本内；数据集来源/标签体系未文档化。
- **为何重要**：模型可复现性与可维护性。
- **观察**：训练数据、标签映射、最佳模型如何产出与分发（`outputs/best_model` 不在仓库）？
- **结论（2026-06-07，维护者提供）** ✅：**基础检查点** = `MCG-NJU/videomae-base-finetuned-kinetics`（HuggingFace；VideoMAE base，Kinetics-400 微调），在其上**再微调**到异常行为分类；**微调数据与标签** = AI Hub「이상행동 CCTV 영상」(Abnormal Behavior CCTV Video)，dataSetSn=171，717h / 8,436 clips / 12 类。详见 [ai-architecture.md §1.1 模型与数据来源](../architecture/ai-architecture.md)。**剩余待办**（不阻断本项关闭）：训练超参应从脚本固化回 `configs/train.yaml`；`best_model` 分发方式属运维问题（OQ-OPS 关联）。

### OQ-AI-3 ｜事件类型枚举与模型标签的对应关系
- **证据** ✅：DB `event_type_enum` 有 13 类（ASSAULT/FIGHT/…）；实时脚本 `target_label` 默认仅 `"assault"`。
- **为何重要**：模型实际能识别哪些类别、与业务枚举如何映射，决定检测覆盖面。
- **观察**：模型标签集与 `event_type_enum` 的完整对应未记录。
- **结论（2026-06-07，复核 + 维护者提供）** ✅：AI Hub 12 类与 `event_type_enum`（13 值）**几乎 1:1**——枚举显然照该数据集分类体系设计：폭행→`ASSAULT`、싸움→`FIGHT`、절도→`BURGLARY`、기물파손→`VANDALISM`、실신→`SWOON`、배회→`WANDER`、침입→`TRESPASS`、투기→`DUMP`、강도→`ROBBERY`、데이트폭력/추행→`DATEFIGHT`、납치→`KIDNAP`、주취행동→`DRUNKEN`；第 13 值 `OTHER` 为 catch-all。本项**实质解决**；**仅剩实现细节**：确认微调模型 `id2label` 实际输出的 label 字符串，据此写"模型 label → event_type_enum"查表（属 [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md) 实现项）。映射表见 [ai-architecture.md §1.1](../architecture/ai-architecture.md)。

---

## 运维（OPS）

### OQ-OPS-1 ｜CI 每次部署删除数据卷是否符合预期？
- **证据** ✅：`Jenkinsfile` 执行 `docker compose down --volumes`，清空 `postgres_data`/`neo4j_data`。
- **为何重要**：演示环境=每次重置（合理）；生产=数据丢失（严重）。
- **观察**：目标部署环境是哪种？
- **结论（2026-05-29，团队确认）** ✅：当前面向**演示重置**（每次清库重建），符合预期。**生产环境将一并去除「删卷」与「插入 seed」两步流程**（届时需独立的生产部署流水线）。已 **Accept** [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)（2026-05-29 签署；推荐 Flyway/Liquibase 作 schema 迁移；落地待 Implementation）。

### OQ-OPS-2 ｜Redis 的角色
- **证据** ✅：`db/redis-docker-compose.yml` 存在，且 `db/README.md` 开头提到"redis 으로 user 테이블…"；但根 compose 与后端依赖中**未见 Redis**。
- **为何重要**：Redis 是历史遗留、计划中、还是某子流程在用？
- **观察**：当前主链路似乎不依赖 Redis。
- **结论（2026-06-07，维护者口述）** 🔶：Redis **原拟用于服务端 session 存储**（与 `db/README.md` 开头"redis 으로 user 테이블…"一致）；项目早期考虑过 Redis session，后改为无状态 JWT（[ADR-0007](../decisions/adr/ADR-0007-jwt-stateless-auth.md)），Redis 随之弃用——`redis-docker-compose.yml` 即该弃案遗留。**更新（2026-06-07，已定案）** ✅：会话机制定为**服务端 session**（[ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md)，取代 ADR-0007）——**Redis 弃案复活、复用为 session store**，将并入主 `docker-compose.yml`。本项关闭。

### OQ-OPS-3 ｜TLS/HTTPS 终结位置
- **证据** ✅：仓库内 Nginx/compose 无 TLS 配置，前后端走 HTTP。
- **为何重要**：生产需加密传输（尤其涉及儿童 PII）。
- **观察**：由外层基础设施终结，还是尚未配置？
- **结论（2026-06-07）** ✅：因 [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md) 的 `Secure` 会话 cookie，HTTPS 从"待确认"升级为**生产硬要求**；已立 [ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md)（Proposed）：边缘反代终结 TLS + HTTP→HTTPS + HSTS。本项归 ADR-0017 跟踪。

### OQ-OPS-4 ｜回滚与发布策略
- **证据** 🔶：CI 为全量重建，无显式回滚。
- **观察**：正式发布/回滚策略未记录。

---

## 数据（DATA）

### OQ-DATA-1 ｜PostgreSQL → Neo4j 同步策略
- **证据** ✅：data-loader 一次性加载；无增量同步。
- **为何重要**：PG 数据变更后图视图过时。
- **观察**：图的刷新/增量机制是否需要？

### OQ-DATA-2 ｜`relationship_enum` 取值不足
- **证据** ✅：枚举仅 `FATHER`/`MOTHER`，但列注释提到"부/모/조부모/후견인"（父/母/祖父母/监护人）。
- **观察**：是否需扩展以覆盖非父母监护人？

### OQ-DATA-3 ｜`notifications` 多个 NOT NULL 字段的合理性
- **证据** ✅：`sent_at`、`fail_reason`、`retry_count` 等为 `NOT NULL`，但语义上新建通知时可能尚无值。
- **观察**：是否应放宽为可空？（事实记录，不下结论）
- **结论（2026-06-07）** ✅：纳入 [ADR-0018 通知子系统](../decisions/adr/ADR-0018-notification-subsystem.md)（Proposed）——"待发"态应允许 `sent_at`/`fail_reason`/`retry_count` 为空/默认；放宽 NOT NULL 的 schema 变更走 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md) 迁移。本项归 ADR-0018 跟踪。

### OQ-DATA-4 ｜`menu` / `common_codes` 两张平台字典表的设计复审
- **证据** ✅：`02_menu.sql` 建 `menu`（单数）、`03_CommonCode.sql` 建 `common_codes`（复数）；二者命名风格与核心 28 表不一致；`menu` 有 `MenuController` 但后端**无 `Menu` 实体**；本知识库此前误写为 `common_code`（单数）。
- **背景（2026-05-29，团队确认）**：这两张表**非原作者设计**，其结构/逻辑/命名可能存在潜在问题。
- **观察**：是否需要重新设计、统一命名规范、补齐缺失实体？「是否有更改设计的必要性」待评估——提案见 [ADR-0013](../decisions/adr/ADR-0013-dictionary-tables-governance.md)。
- **结论（2026-05-29，团队确认）** ✅：已 **Accept** [ADR-0013](../decisions/adr/ADR-0013-dictionary-tables-governance.md)（2026-05-29 签署）。`menu` → **C 静态方案**（移至前端配置 / 移除 DB 表+Controller+seed / 文案改 i18n 键）；`common_codes` → **β 后端枚举元数据端点 + 前端 i18n**（新增极小 `GET /api/v1/enums/{name}` 反射 Java enum / 移除 `common_codes` 表 + 全部 CRUD 栈 / 前端用 labelKey 解 i18n / 加 CI 校验"PG enum = Java enum"）；落地待 Implementation。

---

## 产品（PROD）

### OQ-PROD-1 ｜缺少产品需求文档（PRD）
- **证据** ✅：仓库无 `docs/product` 之外的产品文档；产品意图靠代码逆向。
- **为何重要**：优先级、商业模式、目标市场无权威来源。
- **观察**：是否存在仓库外的产品文档？

### OQ-PROD-2 ｜前端覆盖 < 后端 API 全集
- **证据** ✅：班级/教室/保护者等完整 CRUD 在前端无对应页面。
- **观察**：是按场景裁剪，还是前端未完成？

### OQ-PROD-3 ｜密码重置未实现（及其余 `Not implemented` 占位端点）
- **证据** ✅：`AuthService.passwordResets` 抛 `Not implemented`；`AuthController` 中 `logout`、`changePassword`、`password-resets/{token}`、两个 `verification-codes` 端点同样 `throw "Not implemented"`。
- **观察**：是否为待开发功能？
- **结论（2026-05-29，团队确认）** ✅：上述全部 `Not implemented` 端点均为**「待开发」占位**（非废弃）。已连通 service 的认证端点仅 `login`/`refresh`/`register`/`register/availability`。

### OQ-PROD-4 ｜角色档案复用（KINDERGARTEN_ADMIN↔TEACHER、PLATFORM_IT_ADMIN↔SUPERADMIN）
- **证据** ✅：注册逻辑复用同一档案，代码注释 `// 관리자는 없어서 같이 공유함`。
- **观察**：临时方案还是终态？

---

## 架构 / 测试（ARCH / TEST）

### OQ-ARCH-1 ｜API 版本化意图（`/v1`）
- **证据** ✅：包名 `com.ai_kids_care.v1` 与路径 `/api/v1`；前端有 `LEGACY_API_BASE_URL`（去 `/v1`）兼容"遗留端点"。
- **观察**：存在哪些"遗留端点"？版本演进策略为何？

### OQ-ARCH-2 ｜统一错误处理与响应格式
- **证据** 🔶：未见 `@ControllerAdvice`；service 抛通用异常。
- **观察**：错误响应契约是否需统一（影响前端处理）？

### OQ-ARCH-3 ｜`pg-spring-crud-codegen` 拆分为独立工程的意图
- **证据** ✅：代码生成器位于 `pg-spring-crud-codegen/`（**2026-05-29 由 `scripts/codegen/` 迁入**，旧路径仅留 README 软指针）。Python + psycopg + pystache，见 [ADR-0004](../decisions/adr/ADR-0004-layered-backend-codegen.md) 与 [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md)。
- **背景（2026-05-29，团队确认）**：`pg-spring-crud-codegen` 原意是把「通过数据库逆向工程自动生成 Java 代码」的能力**分离为独立子工程**，以便后期从本仓库拆出。
- **观察**：拆分时机/边界/产物归属待定；属 module-boundary 变更，提案见 [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md)。
- **结论（2026-05-29，团队确认）** ✅：已 **Accept** [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md)（`pg-spring-crud-codegen` = `scripts/codegen` 同一 Python 工具的预留迁址位置，非重写；执行方案 A 仓内迁址 + 软指针，日后 `git filter-repo` 带史拆出）。**实施记录（2026-05-29）**：迁址完成；docker-compose 相对路径已修正；两处 README 就位；CODEOWNERS 已补条目；13 处内部引用已切换。

### OQ-ARCH-4 ｜列表端点 `keyword` 过滤：14/17 为「静默空操作」
- **证据** ✅（2026-06-07 复核）：17 个列表 Controller 暴露 `@RequestParam(required = false) String keyword`（出现在 Swagger 中），但其中 **14 个对应 Service 直接 `repository.findAll(pageable)` 忽略该参数**（统一注释 `// TODO: filter X by keyword`：`AiModel`/`AppreciationLetter`/`AuditLog`/`Class`/`DetectionEvent`/`DetectionSession`/`DeviceToken`/`EventEvidenceFile`/`Guardian`/`Notification`/`NotificationRule`/`Room`/`Superadmin`/`User`）；仅 **3 个真正实现过滤**：`KindergartenService`（`findByNameContains`）、`AnnouncementService`（`listActiveAnnouncements`）、`TeacherService`（`findByNameContains*`）。
- **为何重要**：API 契约对外宣称支持 `keyword` 搜索（Swagger 可见、调用方返回 HTTP 200），但 14 个端点**静默返回未过滤结果且无任何报错**——属「伪实现」，比 `Not implemented`（会显式抛错）更隐蔽，最易在演示中被误判为已完成。根因为 codegen 模板统一生成参数后未回填实现（关联 [ADR-0004](../decisions/adr/ADR-0004-layered-backend-codegen.md)）。
- **观察**：这 14 个端点是「计划实现过滤」还是「应移除误导性参数」？需团队确认终态。若选择实现，建议在 codegen 模板层统一补齐（如按可搜索列生成 `Containing` 派生查询），避免逐个手写造成 3 已实现 vs 14 未实现的持续漂移。

### OQ-TEST-1 ｜测试策略
- **证据** ✅：后端/前端/AI 均无自动化测试。
- **为何重要**：无回归保护，改动风险高（见 [testing](../engineering/testing.md)）。
- **观察**：是否有仓库外测试？目标测试策略为何？

---

## 语言治理（LANG）

> OQ-LANG-1..5 为设计阶段问题，已由负责人解答并固化于 [ADR-0008](../decisions/adr/ADR-0008-language-governance.md)（见其附录）。以下为仍开放项。

### OQ-LANG-6 ｜产品 UI 的 i18n 机制选型
- **证据** ✅：[ADR-0008](../decisions/adr/ADR-0008-language-governance.md) 固定了 i18n 的**语言角色**（消息键中立、`ko` 必出货、`zh` 作者参考、多语增量、locale 为发布资产），但**机制未定**。
- **为何重要**：前端为 Next.js 静态导出（见 [ADR-0005](../decisions/adr/ADR-0005-frontend-static-export.md)），i18n 库/文件格式/构建集成方式影响可维护性与发布门禁（`ko` 完整性校验）的落地。
- **观察**：选型（如 next-intl / react-i18next / 自研 JSON 资源）待定，建议另立 ADR。

---

## 使用方式

- 与团队对齐时，逐项确认并在此记录结论（可追加"**结论/Decision**"字段）。
- 一旦某项形成决策，应将其固化为 [ADR](../decisions/adr/README.md)，并更新相关文档的 ❓ 标注为 ✅/🔶。
