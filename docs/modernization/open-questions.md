# 开放问题登记册（Open Questions）

> 本登记册汇总**仅凭代码无法判定、需要团队（尤其原作者）确认**的事项。每项给出：证据、为何重要、当前观察。**这里不给答案、不给方案**——只把问题摆清楚（Discovery 模式）。

约定：编号 `OQ-<域>-<序号>`，被全库文档交叉引用。域：SEC（安全）、OPS（运维）、AI（AI/集成）、DATA（数据）、PROD（产品）、ARCH（架构）、TEST（测试）。

---

## 安全（SEC）

### OQ-SEC-1 ｜后端鉴权当前关闭，是临时还是疏漏？
- **证据** ✅：`SecurityConfig.java` 中 `/api/v1/**` 为 `permitAll()`，且 `addFilterBefore(jwtAuthFilter, ...)` 被注释。
- **为何重要**：全部业务 API 对无凭证请求开放；角色/多租户模型未被强制执行。
- **观察**：前端实现了完整 JWT/refresh 且注释提到遇到过 401 → 🔶 推断鉴权曾开启、当前为开发/演示临时放开。需确认目标态与开启时机。

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

---

## AI 与集成（AI）

### OQ-AI-1 ｜AI 检测结果为何不回流后端/数据库？
- **证据** ✅：`ai/` 全目录无 DB/后端调用；实时告警仅 Pushover/SMS + CSV。`detection_events` 等靠种子。
- **为何重要**：这是产品完整性的核心——"检测→落库→复核→展示→通知"闭环未连通。根 README 称实时链路为"实验"。
- **观察**：闭环是计划中的下一步，还是有意保持解耦？集成契约（谁写库、用何协议）待定。

### OQ-AI-2 ｜训练配置与数据集来源
- **证据** ✅：`configs/{train,eval,infer}.yaml` 为空；训练超参在脚本内；数据集来源/标签体系未文档化。
- **为何重要**：模型可复现性与可维护性。
- **观察**：训练数据、标签映射、最佳模型如何产出与分发（`outputs/best_model` 不在仓库）？

### OQ-AI-3 ｜事件类型枚举与模型标签的对应关系
- **证据** ✅：DB `event_type_enum` 有 13 类（ASSAULT/FIGHT/…）；实时脚本 `target_label` 默认仅 `"assault"`。
- **为何重要**：模型实际能识别哪些类别、与业务枚举如何映射，决定检测覆盖面。
- **观察**：模型标签集与 `event_type_enum` 的完整对应未记录。

---

## 运维（OPS）

### OQ-OPS-1 ｜CI 每次部署删除数据卷是否符合预期？
- **证据** ✅：`Jenkinsfile` 执行 `docker compose down --volumes`，清空 `postgres_data`/`neo4j_data`。
- **为何重要**：演示环境=每次重置（合理）；生产=数据丢失（严重）。
- **观察**：目标部署环境是哪种？

### OQ-OPS-2 ｜Redis 的角色
- **证据** ✅：`db/redis-docker-compose.yml` 存在，且 `db/README.md` 开头提到"redis 으로 user 테이블…"；但根 compose 与后端依赖中**未见 Redis**。
- **为何重要**：Redis 是历史遗留、计划中、还是某子流程在用？
- **观察**：当前主链路似乎不依赖 Redis。

### OQ-OPS-3 ｜TLS/HTTPS 终结位置
- **证据** ✅：仓库内 Nginx/compose 无 TLS 配置，前后端走 HTTP。
- **为何重要**：生产需加密传输（尤其涉及儿童 PII）。
- **观察**：由外层基础设施终结，还是尚未配置？

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

---

## 产品（PROD）

### OQ-PROD-1 ｜缺少产品需求文档（PRD）
- **证据** ✅：仓库无 `docs/product` 之外的产品文档；产品意图靠代码逆向。
- **为何重要**：优先级、商业模式、目标市场无权威来源。
- **观察**：是否存在仓库外的产品文档？

### OQ-PROD-2 ｜前端覆盖 < 后端 API 全集
- **证据** ✅：班级/教室/保护者等完整 CRUD 在前端无对应页面。
- **观察**：是按场景裁剪，还是前端未完成？

### OQ-PROD-3 ｜密码重置未实现
- **证据** ✅：`AuthService.passwordResets` 抛 `Not implemented`。
- **观察**：是否为待开发功能？

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

### OQ-TEST-1 ｜测试策略
- **证据** ✅：后端/前端/AI 均无自动化测试。
- **为何重要**：无回归保护，改动风险高（见 [testing](../engineering/testing.md)）。
- **观察**：是否有仓库外测试？目标测试策略为何？

---

## 使用方式

- 与团队对齐时，逐项确认并在此记录结论（可追加"**结论/Decision**"字段）。
- 一旦某项形成决策，应将其固化为 [ADR](../decisions/adr/README.md)，并更新相关文档的 ❓ 标注为 ✅/🔶。
