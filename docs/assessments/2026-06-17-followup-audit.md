---
type: assessment
date: 2026-06-17
status: Current
supersedes: 2026-06-10-codebase-audit.md
baseline_commit: d0d2269
scope: 复核 2026-06-10 审计发现的解决状态，并记录 2026-06-17 多智能体探索的新发现与纠偏
method: 只读代码勘察（多 agent 并行 Grep/Read 抽样），未在本轮运行 build/test
---

# AI Kids Care 复核审计（2026-06-17）

## 执行摘要

本审计复核 `2026-06-10-codebase-audit.md`（baseline `ead603e`）的发现在 baseline `d0d2269` 上的解决状态，并叠加一轮针对性方案探索的新发现。

核心结论有三：

1. **06-10 审计的多个 P0 已被后续 SPEC-0001 安全工作解决**：`/api/v1/**` 现为 `authenticated()` 默认拒绝（非 `permitAll`），public VO 已移除 `passwordHash`/RRN hash/`storageUri`/`pushToken` 等 S0/S1 字段，前端已从 localStorage 推断改为服务端 cookie session。06-10 审计的 `status: Current` 已不再准确，本文件取代之。

2. **06-10 审计的部分 P0/P1 在严格核实后被证明是「潜在债务」而非「活跃暴露」**：被点名的 8 个无租户过滤 `findAll` Service 对应的 Controller 全是空壳（零 handler、零注入点），路径落入默认拒绝；多个「抛 500」的 stub 实际不可达（无路由注册）。这些应按防御性加固而非紧急泄露处理。

3. **真正确证、低成本、应最先处理的暴露面集中在三处**：production compose overlay 未关闭数据存储端口且保留弱默认密码；AI 脚本中硬编码的 Pushover user key；前端登录页硬编码的演示账号密码。

> 方法限制：本轮为只读代码勘察，未运行 backend test / frontend build / compose config。下方「当前状态」均基于 baseline `d0d2269` 的代码抽样，行为级结论仍需按 `.ai/project.md` 的验证命令复核。

## 2026-06-10 发现的解决状态复核

| ID | 原 Priority | 原发现摘要 | 当前状态 | 证据 / 说明（baseline d0d2269） |
| --- | --- | --- | --- | --- |
| SEC-001 | P0 | 全部业务 API 匿名开放，role/tenant 未强制 | **已大幅解决** | `SecurityConfig` 现将 `/api/v1/**` 设为 `authenticated()`；SPEC-0001 已落地 `AuthorizationPolicy` + method-level `@PreAuthorize` + scoped JPQL（如 `NotificationService`/`NotificationRepository`、`GuardianChildPolicy`） |
| SEC-002 | P0 | `UserVO` 返回 `passwordHash`，child/guardian/teacher VO 返回 RRN hash | **已解决** | SPEC-0001 Phase 1A 已从 public VO 移除 S0 字段；`SensitiveResponseContract`/`PublishedOpenApiContract` 类测试守护 |
| SEC-003 | P0 | JWT/DB/Neo4j/camera 默认值；AI 告警收件人写死 | **部分解决** | AI `pushover.py` 已改为 `os.getenv` 读取；但 `ai/scripts/stream_live_alert_service.py:489` 仍硬编码两个 Pushover user key；DB/Neo4j 弱默认密码 fallback 仍在 compose 与 `application.yml`；backend `NotificationService` 仍向 Pushover 传空 token（写路径未注册，不可达） |
| FLOW-001 | P0 | Event review 字段不匹配 + 非原子写入 | **暂时搁置（非解决）** | `DetectionEventController`/`EventReviewController` 为空壳，问题代码仍在；「关闭控制器」是止血，重开前置条件未文档化（见新发现 N-7） |
| DATA-001 | P0 | Neo4j 存储图查询不需要的 PII | **部分解决** | `db/ne4j_kindergartens/no*.py` loader 顶部已加 SPEC-0001 §365 注释，声明不再投影 S0/PII；端到端 projection 重做仍未完成 |
| OPS-001 | P0 | production compose 在 Flyway 旁启动 loader，有竞态 | **未复核（本轮未展开）** | 需结合 compose merge + 启动顺序验证，留待后续 |
| AUTH-001 | P1 | 密码重置 path 不一致；多个 endpoint 占位 | **部分解决/可下线** | `AuthService.passwordResets` 直接抛异常但**无路由注册**，不可达；前端已有 "준비 중" 占位页可复用 |
| API-001 | P1 | Controller 不用 `@Valid`，无全局 error contract | **未解决** | 多个 Controller 的 `@RequestBody` 仍缺 `@Valid`；`ApiExceptionHandler` 仅处理 `EntityNotFoundException`；`server.error.include-stacktrace` 未显式设为 never |
| FE-001 | P1 | `ProtectedRoute` 未用；localStorage 作 session authority；从 demo ID 推断 scope | **已解决** | 已改为服务端 cookie session（`SessionBootstrap`），不再从 demo ID/JWT 客户端解析推断身份 |
| GRAPH-001 | P1 | Graph loader 混用 CSV + 单 PG import；查询假设单行 | **未复核** | 留待后续 |
| CI-001 | P1 | 仅 gate backend test；frontend lint 失败 | **部分改善** | CI 已迁移到 GitHub Actions（`Backend Java Tests` + `Compose Config` + `schema-digest-drift`）；frontend lint/build gate 与全仓 lint 红仍待确认 |
| DB-001 | P1 | DBML/initdb/Flyway 独立维护无 CI 校验 | **已部分加护栏** | `schema-digest.sh` + `schema-digest-drift.yml` 提供 schema 漂移检测 |
| AI-001 | P1 | AI config 空，行为硬编码，无 persistence contract/测试 | **未解决** | `ai/tests` 仍不存在；行为仍硬编码；详见新发现 N-5 |
| DOC-001 | P1 | 文档混合当前事实/未来意图/历史快照 | **本审计即在处理** | ADR status/implementation 字段语义不一致仍存在（见新发现 N-6） |
| API-002 | P2 | 列表 endpoint 静默忽略 `keyword`；CRUD 暴露内部字段 | **未解决（已降级）** | `keyword` 仍被忽略；多为空壳 Controller 不可达，实际影响低于原判 |

## 2026-06-17 新发现与纠偏

> 标注「纠偏」者为对 06-10 或本轮初判的修正，是本审计最重要的部分。

### N-1 production compose 未关闭数据存储端口 + 弱默认密码（真实 P0）

`docker-compose.yml` 发布 PostgreSQL 5432、Redis 6379、Neo4j 7474/7687 到宿主；`docker-compose.prod.yml` 仅对 frontend 用 `ports: !reset null`，**未对三个数据存储做 reset**，prod 栈仍向宿主暴露这些端口。弱默认密码 `${NEO4J_PASSWORD:-rose100!}`、`${POSTGRES_PASSWORD:-kids_pass}` 写死在 compose 与 `application.yml`；Redis 无 `requirepass`。

- 处置：prod overlay 对三个服务加 `ports: !reset null`；`${VAR:-weak}` 改为必填 `${VAR:?...}`；Redis 加 `requirepass`。
- 风险：高。验证：`docker compose -f docker-compose.yml -f docker-compose.prod.yml config` 后确认无对应 `published` 行。
- 顺带：Neo4j 缺 healthcheck，backend/data-loader 用 `condition: service_started`（应改 `service_healthy`，与 N-1 密码改动有顺序依赖）。

### N-2 硬编码 Pushover user key（真实 P0，需轮换）

`ai/scripts/stream_live_alert_service.py:489` 硬编码两个 Pushover user key 并已提交。`pushover.py` 已 env 化，此处遗漏。

- 处置顺序强绑定：**先轮换 → 再 env 化（`PUSHOVER_USER_KEYS`）→ git 历史清理视扫描结果**（轮换后历史清理收益有限，可延后）。同文件 `:562` 硬编码生产 FLV URL，一并改为 `STREAM_URL` 环境变量。
- 风险：中（user key 非写权限凭据，危害有限，但属已提交泄露）。

### N-3 前端硬编码演示账号密码（真实 P0）

`frontend/src/components/auth/LoginForm.tsx:141-159` 明文展示五组演示账号密码，编入静态导出镜像分发。

- 处置：用 `NEXT_PUBLIC_SHOW_DEMO_HINTS` 环境变量门控，生产构建设为 false；或直接移除。
- 风险：中（取决于是否区分演示机与真实生产部署，见开放问题）。

### N-4 纠偏：8 个「无租户过滤 findAll」Service 实为空壳隐患，非活跃泄露

06-10 与本轮初判担心 `GuardianService`/`UserService`/`AuditLogService`/`AppreciationLetterService`/`DeviceTokenService`/`NotificationRuleService`/`SuperadminService`/`EventEvidenceFileService` 的 `findAll(pageable)` 跨租户泄露。**严格核实：8 个对应 Controller 全是空壳（仅 `@RestController`+`@RequestMapping`，零 handler），且 8 个 Service 在全仓零注入点**，路径落入 `/api/v1/**` 默认拒绝 → 匿名 401、已认证无 handler → 404/405，Service 代码从不可达。

- 处置（降级为防御性加固）：在 Service 方法加 `@PreAuthorize("denyAll()")` 封口，防止未来误补 handler 时遗漏授权。
- 路线分流：`User`/`Superadmin`/`AuditLog`/`DeviceToken`/`EventEvidenceFile` 走「长期不开/另立专用只读端点」（无合法园级 list 语义或属平台级）；`Guardian`/`AppreciationLetter`/`NotificationRule` 排期走「补租户过滤」（复用 SPEC-0001 + `authz-read-slice` skill 的三层模式：`@PreAuthorize` 门 + role-branch + scoped JPQL）。

### N-5 AI 服务加固（已定方向）

- 纠偏：AI **不在生产栈**、backend 不经 HTTP 调用它、Caddy 不代理 8001、`/health` 已实现、单 worker（非多 worker 各自加载）。暴露面取决于宿主防火墙。
- **已决策**（2026-06-17）：AI 服务网络形态采用**仅内网隔离**（`expose` 替代 `publish`，不并入主栈对外端口）；`/predict/path` 端点**直接删除**（流推理在进程内直调 `VideoPredictor`，无外部按路径调用场景，删端点比加白名单更彻底地消除路径遍历面）。
- 仍需处理：`/predict/upload` 加大小上限（`AI_MAX_UPLOAD_MB`）+ 扩展名白名单 + magic-byte 校验（DoS）；`stream_live_alert_service.py` 的 `frame_buffer` deque 加 `maxlen` 保险丝（内存增长）；帧采样函数四处重复归一到 `ai_app.inference.pipeline`；补依赖声明（`tqdm`/`solapi`，确认 `scipy`）与 lock；补焦点测试（`update_persistence_state` 状态机、`sample_frame_indices`、upload 路径）。`infer.py` 的 `lru_cache(maxsize=50000)` 降为有界值。

### N-6 纠偏：stub 与契约漂移多为不可达或已被托底

- `AuthService.passwordResets` 抛异常但无路由 → 不可达死代码，移除 throw 即可。
- `NotificationService` 向 Pushover 传空 token，但写路径（POST/PUT/DELETE）未注册 → 不可达；应在 `PushoverService` 入口加空凭证 fast-fail guard 防未来误开。
- 6 个空壳 Controller（`DetectionEvent`/`User`/`AuditLog`/`Superadmin`/`Graph`/`AppreciationLetter`）返回 405；删除 Controller 类使其回退 404 更诚实。
- 公告 `createdAt`/`updatedAt`/`authorId` 由前端生成 POST，但**后端 MapStruct 已 `ignore` 这些字段 + `requireSameUser` 校验 authorId，无伪造漏洞**，仅 DTO 契约污染 → 清理写 DTO 字段 + 前端 payload。
- 感谢信前端 API 全是 throw stub + localStorage 假数据 → 四个路由切到已存在的 `AppreciationLettersUnavailable` 组件并移除 localStorage 缓存（避免「写入成功」假象）。

### N-7 DetectionEvent/EventReview 重开前置条件未文档化（结构性风险）

`DetectionEventController` 被有意关闭以暂缓安全债，但 FLOW-001 描述的 snake_case 不匹配 + 非原子写入问题仍在代码中。「关闭控制器」是止血非修复。

- 处置：在 SPEC-0001 或新 SPEC 中明确重开前置条件：① 前后端契约（casing）统一，② review 与 status 写入收敛为单一事务，③ tenant-aware policy 接入。防止后续 agent 不知前因直接重开，把 P0 带回可访问 API。

### N-8 ADR-0010 已否决 RRN 可逆加密（关键纠偏）

本轮初判建议 RRN 由 BCrypt 迁移到 AES-GCM。**核实：ADR-0010（Accepted，2026-05-29）明确否决任何 RRN 可逆加密，选定方案为 HMAC-SHA-256 + pepper（单向）。** `AesGcmCryptoUtil` 不应用于 RRN（其零业务调用是另一回事）。

- 现状：`AuthService` 注册用 BCrypt `encode(rrnBack7)` 存 `rrn_encrypted`，`ChildrenService` 用 `matches()` 逐行比对（候选集 + BCrypt 双重 O(N)，且无法建唯一约束）。
- 处置（HIGH RISK，需 ADR + 人类批准）：新建 **ADR-0024** 定义 BCrypt→HMAC 的「双读单写」过渡、回填触发时机、删旧列门控；含 Flyway V4（新增 `rrn_hash` + 唯一索引）/ V5（NOT NULL+UNIQUE）/ V6（删 `rrn_encrypted`）、种子数据重生成、pepper 密钥治理（与 JWT secret 同范式）。存量 BCrypt 不可反推，只能用户下次输入原文时回填。
- 关联：`CameraStream` 的 `streamPasswordCiphertext/iv/keyVersion` 三列有 schema 但 `AesGcmCryptoUtil` 零调用、无密钥配置——是另一个「计划内未实现」面，需独立确认（见开放问题）。

## 建议处置顺序

1. **真实 P0（确证暴露、低成本、可立即实现）**：N-1 端口收口+弱密码必填化、N-2 Pushover key 轮换+env 化、N-3 前端演示账号门控。
2. **真实 P1（防御性 + 诚实化）**：N-4 八个空壳 Service `denyAll` 封口；N-6 stub 诚实化（感谢信路由切 Unavailable、删空壳 Controller、公告 DTO 清理、Pushover guard）；N-5 AI 上传限额 + 删 `/predict/path` + 内网隔离。
3. **结构性 / 高风险（需 ADR + 批准）**：N-8 起草 ADR-0024 后实现 RRN HMAC 迁移；N-7 文档化 DetectionEvent 重开前置条件。
4. **质量整固**：N-5 AI 帧采样去重/依赖锁/焦点测试；API-001 `@Valid` + 全局 error contract；API-002 `keyword` 过滤实现或文档化。

## 本轮已采纳的决策

- AI 服务网络形态：**仅内网隔离**（`expose` 不 `publish`）。
- `/predict/path`：**直接删除端点**。

## 待决策（阻塞相关实现）

- **ADR-0024 门控**：RRN 存量回填判定「足够完成」的条件（全量 NULL 归零 vs 自然消减）；dev/test 环境 pepper 注入方式。
- **演示账号策略**：是否存在区别于演示机的真实生产部署？若不区分，演示账号可见性本身非安全问题（只要 DB seed 中这些弱账号在生产不存在）。
- **CameraStream 加密链路**：三列+`AesGcmCryptoUtil` 是计划内未实现，还是需独立 ADR 确认是否实现摄像头密码可逆加密？长期为空会与 ERD 注释不符。
- **schema-digest.sh 硬编码 `kids_pass`**：临时容器、风险低，是否随手改为 `${POSTGRES_PASSWORD:-kids_pass}`。

## 开放问题（延续 06-10 未决项）

- AppreciationLetter 接收方语义（GUARDIAN 只能看以自己为 target 的，还是本园所有 `isPublic`）——影响路线 A 的 Repository 查询设计。
- DeviceToken 的正确暴露模式应为 `GET /users/me/device_tokens` 而非通用列表。
- TopBar 动态菜单若含指向「准备中」功能的 path，需在 menu 数据层同步下线（超出代码改动范围的 follow-up）。
- OPS-001、GRAPH-001 本轮未展开复核，状态待定。
