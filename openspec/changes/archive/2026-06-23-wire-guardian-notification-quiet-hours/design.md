## Context

闭环步骤③b。已核实(③a recon + 本次):

- 项目**无任何定时任务机制**(`@EnableScheduling`/`@Scheduled`/ShedLock 全无)。
- `kindergartens` 表/entity 无 quiet_hours 字段;`Notification` 无 `deferred_until`;`NotificationStatusEnum` = `QUEUED/SENDING/SENT/DELIVERED/READ/FAILED/CANCELED`(无 `DEFERRED`)。
- Flyway 当前最高 `V8`;`data-platform` spec 规定 schema 演进:改 `db/dbml/schema.dbml`(权威)→ `dbml2sql` 生成 `db/initdb/01_create_schema.sql`(initdb/demo baseline)→ 新 Flyway `VN`(prod 增量);`SchemaConsistencyGuardTest` 断言两路径(initdb+baseline、fresh-V1)收敛到同一终态。
- ③a `GuardianNotificationService.notifyOnReview` 构建 `Notification(PUSH, QUEUED)` 后直接 `notificationService.dispatch`;`dispatch` 设 `SENDING` → Pushover → `SENT/FAILED`,不校验入状态。
- `NotificationStatusEnum` 已含 PG named enum;`notifications.fail_reason` 在 initdb 期 NOT NULL(V3 后 relax)——新增列要注意 initdb 期约束。

## Goals / Non-Goals

**Goals:** 全园 quiet_hours;RESOLVED 落静音 → 延后到时段结束补发;ESCALATED 穿透即时;定时扫描补发。Asia/Seoul。

**Non-Goals:** 每家长 quiet_hours(预留签名);ShedLock(单实例);规则引擎;SMS;前端。

## Decisions

### D1:quiet_hours 存园级列,解析抽象预留 per-guardian 演进
`kindergartens` 加 `notification_quiet_hours_json varchar`(nullable)。`QuietHoursService.resolveQuietWindow(Long kindergartenId, Long guardianUserId)` 一期忽略 `guardianUserId`、只读园配置;演进时该方法先查 per-guardian override 再 fallback 园,**调用方签名不变**(不堵死,符合决策)。

### D2:延后模型 = DEFERRED 状态 + deferred_until
`Notification` 加 `deferred_until timestamptz`(nullable);`NotificationStatusEnum` 加 `DEFERRED`。RESOLVED 落静音 → 构建 `Notification(status=DEFERRED, deferred_until=nextEnd)`,**save 但不 dispatch**。

### D3:quiet 判断(Asia/Seoul,跨午夜)
`QuietHoursService` 用 `ZoneId.of("Asia/Seoul")`(UTC+9 无 DST)。解析 `{"start":"HH:mm","end":"HH:mm"}`:
- 取 `instant` 的首尔本地时间 `t`。同日窗(start<end):静音 = `start<=t<end`。跨午夜窗(start>end,如 22:00–07:00):静音 = `t>=start || t<end`。
- `nextEndInstant`:静音时段的结束时刻 —— 首尔本地下一个 `end`(若 `t<end` 则今日 end,否则明日 end),转 `Instant`/`OffsetDateTime`。
- 空/null/解析失败 → 不静音(`isWithinQuietHours=false`),防解析问题阻断通知。

### D4:ESCALATED 穿透;只对 RESOLVED 查 quiet
`GuardianNotificationService` 在构建每条 notification 前:`ESCALATED` → 即时(QUEUED + dispatch,③a 不变);`RESOLVED` → 查 `resolveQuietWindow`,若 `isWithinQuietHours(now)` 则 DEFERRED+deferred_until、不 dispatch,否则即时。dedupe_key 不变(延后与即时同 key,防重)。

### D5:扫描补发
主类 `@EnableScheduling`。`DeferredNotificationScanner.scan()` `@Scheduled(fixedDelay=60000)`:`notificationRepository.findByStatusAndDeferredUntilLessThanEqual(DEFERRED, now)` → 逐个 `dispatch`(dispatch 直接置 SENDING→SENT/FAILED,覆盖 DEFERRED,无需先改 QUEUED)。best-effort try/catch per 条。`scan()` public 可同步测。

### D6:schema 三处同步 + enum ADD VALUE 事务约束
V9 迁移:`ALTER TYPE notification_status_enum ADD VALUE IF NOT EXISTS 'DEFERRED';` + `ALTER TABLE kindergartens ADD COLUMN notification_quiet_hours_json varchar;` + `ALTER TABLE notifications ADD COLUMN deferred_until timestamptz;`。PG12+ 允许 `ADD VALUE` 在事务内,但**新值不能在同一事务后续使用**——迁移只加值,运行时(别的事务)才用,安全。`schema.dbml` + `01_create_schema.sql` 同步加列/枚举值(initdb 路径直接含终态)。无 `dbml2sql` 工具则手改 `01_create_schema` 并在 review 核对与 dbml 一致。

## Risks / Trade-offs

- **[enum ADD VALUE 事务约束]** 迁移加值、运行时用,跨事务安全(D6)。若 Flyway 把迁移包成单事务且某 DB 版本拒绝事务内 ADD VALUE,V9 拆成独立迁移或设 `transactional=false`。
- **[schema 三路径漂移]** dbml/initdb/Flyway 任一漏改 → `SchemaConsistencyGuardTest`/`FlywayMigrationSmokeTest` 红。验证兜底。
- **[@Scheduled 测试干扰]** `@EnableScheduling` 可能让测试 context 也周期触发 scan。对策:测试直接同步调 `scanner.scan()` 断言;`fixedDelay` 设较大初值或 test profile 不影响(scan 幂等,即便触发也只补到期的)。apply 第 4 步定。
- **[dispatch 入状态]** dispatch 不校验入状态、直接置 SENDING,故 DEFERRED→dispatch 安全;deferred_until 保留作审计。
- **[回滚]** enum ADD VALUE 不可逆(PG 不支持删枚举值);列可 DROP。schema 迁移已应用的库回滚需谨慎;代码 git 还原。
- **[单实例假设]** `@Scheduled` 无 ShedLock,多实例会重复扫描 + 重复 dispatch;dedupe 唯一约束 + dispatch 幂等性部分兜底,但记 ShedLock follow-up(部署当前单实例)。

## Migration Plan

1. `schema.dbml` + `01_create_schema.sql` + Flyway `V9`(enum 值 + 两列)+ `Kindergarten`/`Notification` entity + `NotificationStatusEnum`;`ddl-auto=validate` 通过。
2. **[TDD]** `QuietHoursService`(跨午夜/同日/空配置/Asia/Seoul/nextEnd)。
3. `GuardianNotificationService` 改(RESOLVED 落静音→DEFERRED)+ 测试。
4. `@EnableScheduling` + `DeferredNotificationScanner` + `NotificationRepository` 查询 + 测试。
5. 容器 `cleanTest` 全套件 + `SchemaConsistencyGuardTest` + `FlywayMigrationSmokeTest` 全绿;spec delta;code review(opus);archive + 合 develop + push。

## Open Questions

- `@Scheduled` 测试隔离:test profile 禁 scheduling vs 直接调 `scan()`。倾向直接调 + scan 幂等,apply 第 4 步定。
- V9 是否需 `transactional=false`(若某 PG 版本拒事务内 ADD VALUE)。apply 第 1 步灌库验。
- quiet_hours_json 的园级默认值(seed 是否给个示例窗,还是 null=不静音)。apply 定;倾向 seed 给 1 个园示例窗以便演示 + 测试。
