## Context

通知 PUSH 路径现状（apply 期已详测）：
- `NotificationService` 在 `channel==PUSH` 时调用 `pushoverService.sendMessage("", "", ...)` —— 两个字面量空串；`PushoverService` 已加 `@NotBlank` 等价校验，故 PUSH 通知运行即抛。application.yml 无 `pushover.*` 键。
- `device_tokens` 表为 FCM/APNS 直推形（`platform IOS|ANDROID`、`push_token`），与选定通道 Pushover（per-user user-key、平台无关）架构不兼容。该表是**死表**：无派发读取、管理 API 未发布(405)故 client 无法写入、生产为空、引用仅限其自包含 stub 栈。
- 读 API（`GET /notifications`、`/{id}`）已正确接线发布，不动。

既有约定：秘密用 `${ENV}` + `@ConfigurationProperties`+`@Validated`（见 `RrnHashConfig`/`InternalAiServiceConfig`/`CameraStreamCryptoConfig`）。schema 走 Flyway，`ddl-auto=validate`，且测试用 `db/initdb` 初始化 + baseline + V2.. + validate（`BaseIntegrationTest`/`FlywayMigrationSmokeTest` 守护三方一致）。新体制下设计取舍记于本文件，不另起 ADR（ADR 已随迁移折叠进 specs，仅余历史行内引用）。

## Goals / Non-Goals

**Goals:**
- 消除 PUSH「运行即抛」隐患：Pushover 凭据配置化、调用点去空串。
- 把寻址模型治理干净：以 provider-aware `push_subscriptions` 取代死表 `device_tokens`（趁零数据零回归面）。
- 实现一个正确、可单测的 PUSH 投递原语（含投递生命周期），供后续规则引擎调用。

**Non-Goals:**
- SMS（ADR-0018 未决；地址在 `users.phone`）、EMAIL。
- 规则引擎派发 + detection 触发（范围 B；阈值未定 + ADR-0015）。
- 发布 push_subscription / notification_rule 管理 API（spec 维持「未发布、405」）。
- 改通知读 API。

## Decisions

### D1：寻址模型用 provider-aware `push_subscriptions`（方案 3），取代 `device_tokens`
```
push_subscriptions
  push_subscription_id  BIGINT PK
  user_id      bigint NOT NULL  → users
  provider     push_provider_enum NOT NULL     -- 初始仅 PUSHOVER
  address      varchar NOT NULL                -- Pushover user-key（未来 FCM/APNS token 复用此列）
  device_label varchar NULL                    -- 可选 Pushover 设备名 / 人类标签
  status       status_enum NOT NULL            -- ACTIVE|PENDING|DISABLED|REJECTED
  last_verified_at timestamptz NULL
  created_at   timestamptz NOT NULL DEFAULT now()
  UNIQUE (user_id, provider, address)
```
- 理由：名实相符（不再用 `push_token`/`platform` 撒谎）；`provider` 列覆盖唯一现实未来变化（换/加推送商）而无需再迁移；不把 SMS/EMAIL 地址塞进来（它们已在 `users`，见 D2）。
- 备选：① 原样复用 `push_token` 存 user-key —— 否决，债留后人、唯一约束语义错；② Pushover 原生表(`pushover_user_key`,`device_name`) —— 次优，绑死 Pushover，换商即再迁；③ 全通道 `delivery_targets` —— 否决，过度设计，与 `users` 的 phone/email 冗余漂移。

### D2：SMS/EMAIL 地址不入表
PUSH 才需要外部注册身份；SMS=`users.phone`、EMAIL=`users.email` 已存在。故 `push_subscriptions` 只服务 PUSH，避免重复建模。

### D3：迁移策略 = V7 前进转换，**不动 initdb**（apply 期核实修正）
- **已核实**：`db/initdb/01_create_schema.sql` 镜像 V1 基线（其 `notifications.sent_at/fail_reason` 仍是 V1 的 `NOT NULL`，V3 之后才 relax）。两条 schema 路径在 V7 执行前都已有 `device_tokens`：demo/test = initdb 建 + baseline V1 + V2..V7；fresh prod = V1 建 + V2..V7。
- 故 Flyway `V7__replace_device_tokens_with_push_subscriptions.sql`：`DROP TABLE device_tokens` → `DROP TYPE device_platform_enum`（前置校验已过：仅 device_tokens 引用）→ `CREATE TYPE push_provider_enum AS ENUM ('PUSHOVER')` → `CREATE TABLE push_subscriptions`。两条路径都能 DROP（前都有表）。
- **不改 `db/initdb/01_create_schema.sql` 的 device_tokens**（它镜像 V1；改了会破坏 baseline 镜像不变量）；**seed `23_device_tokens_seed.sql` 保留**（initdb 阶段 device_tokens 存在、插入 OK；V7 之后被 drop，无害；push_subscriptions 在 demo 起始为空，测试自带数据）。
- `db/dbml/schema.dbml`：更新 device_tokens→push_subscriptions（设计文档卫生；该文件已知漂移、仅手动 generateMigration 用、不进运行时/测试路径，故非载荷）。
- **三方一致硬约束**：V7 迁移 / JPA 实体（PushSubscription）/ 最终 schema 必须一致，否则 `ddl-auto=validate` 启动失败 —— `ContextLoadSmokeTest` + `FlywayMigrationSmokeTest` 会即时抓到（上个 change 建地基的价值）。

### D4：`PushoverService` 重构为可注入，便于单测
- 现 `PushoverService` 内部 `new PushoverRestClient()` 写死、凭据走参数空串。改为构造注入 `PushoverConfig`（apiToken）+ 注入 `PushoverClient`（接口，生产 bean = `PushoverRestClient`）。
- 测试以打桩 `PushoverClient` 验证成功/失败路径，**CI 不打真实 Pushover**。`PushoverConfig` 的 `@NotBlank` fail-fast 用 `@SpringBootTest` context 或绑定测试覆盖。

### D5：投递原语与生命周期，触发器延后
- `NotificationService` 提供 `dispatch(notification)`（或 create+dispatch）：解析收件人 active `push_subscriptions(PUSHOVER)` → 置 `SENDING` → 调 `PushoverService` → 成功置 `SENT`+`sent_at`；失败置 `FAILED`+`fail_reason`+`retry_count++`。
- **谁调用 dispatch** 留给规则引擎 change（范围 B）。本 change 不加 HTTP 写入端点（spec：写操作 405）。原语本身实现并单测 —— 这就是可交付、可回归保护的单元。
- 收件人无 active PUSHOVER 订阅时的行为：记 `FAILED`+原因（或跳过并审计），具体在 spec scenario 钉死。

### D6：保持管理 API 未发布
`push_subscriptions`（原 device_tokens）与 `notification_rules` 的管理 controller 维持空壳 / 405。本 change 只重命名/重塑其 stub 以匹配新模型，不发布端点。

## Risks / Trade-offs

- [迁移/initdb/实体三方漂移 → context 启动失败] → 由 smoke 测试即时抓；任务里三处一起改并跑容器套件验证。
- [`device_platform_enum` 实际被他处引用，DROP 失败] → 任务前置 grep 校验；若被引用则保留枚举仅移除 device_tokens。
- [Pushover client 不可注入则难单测] → D4 显式重构为接口注入；若 client 无接口，包一层薄 wrapper。
- [provider 枚举初期单值显多余] → 接受；这是「换商不再迁移」的小代价，符合方案 3 取舍。
- [真实 Pushover 凭据泄露] → 仅 `${ENV}`，test profile 用非密占位；绝不提交。
- [投递原语无人调用 = 暂无端到端通知] → 接受且明示：本 change 消隐患 + 立正确原语，端到端待规则引擎 change。
- [投递原子性缺口（code review I1）] 外部 Pushover 调用在 `dispatch` 的 `@Transactional` 边界内：若推送成功但随后 SENT save 失败，事务回滚、行停在 `SENDING`（推送已发出）。本 v1 原语暂无 live 触发器，接受此限制；**规则引擎 change 接入触发器前，应加 idempotency key / 两阶段标记**避免重复推送与 SENDING 滞留。`dispatch` 内已加注释。

## Migration Plan

1. 校验 `device_platform_enum` 引用面（仅 device_tokens 才可 DROP 该枚举）。
2. TDD：先写 push_subscriptions 约束测试 + PushoverConfig fail-fast 测试（red）。
3. 改实体/枚举/repo/mapper/vo（DeviceToken→PushSubscription）+ Flyway V7 + initdb + seed，至 context 起、validate 过、约束测试绿。
4. TDD：投递生命周期测试（成功/失败，打桩 client）→ 实现 dispatch + PushoverService 重构 + 去调用点空串 → 绿。
5. application.yml 加 `pushover.*`；application-test.yml 已有 token 模式可仿。
6. 容器内全套件实跑全绿；notifications spec delta 随 change；code review；合 develop。
- 回滚：schema 为 forward-only；若需回退另写 Flyway 逆向迁移。代码改动可 git 还原。生产无 device_tokens 数据，重建无损。

## Open Questions

- `device_label` 是否本期就用（Pushover 按设备定向）？倾向建列但本期不强用，apply 时定。
- demo seed `23_*`：重建为可用 Pushover 测试订阅，还是留空？倾向留最小占位（避免假 user-key 误导），apply 第 3 步定。
- 收件人多个 active 订阅时是否全发 / 去重，apply 投递步骤按 spec 钉死。
