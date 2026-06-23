## Why

闭环步骤③b。③a(`wire-guardian-notification-on-review`,develop `dafdda6`)已让复核确认 → 关系图解析家长 → 即时 PUSH,但**所有通知即时发**。产品决策要求:`RESOLVED` 知会类通知若落在静音时段应**延后到时段结束补发**,`ESCALATED` 紧急类**穿透静音、仍即时**。③a 刻意把 quiet_hours 隔离出来,因为它需要 **schema 迁移**(园级配置 + 延后字段 + 新状态)**+ 项目当前完全没有的定时任务基础设施**(扫描补发)。

本 change(③b)补上这部分:全园统一 quiet_hours 配置 + `RESOLVED` 落静音 → `DEFERRED` + `deferred_until` → 定时扫描到期补发;`ESCALATED` 行为不变(即时穿透)。

## What Changes

- **schema 迁移 V9(high risk)**:`kindergartens` 加 `notification_quiet_hours_json varchar`(全园静音窗,如 `{"start":"22:00","end":"07:00"}`);`notifications` 加 `deferred_until timestamptz`(nullable);`notification_status_enum` 加 `DEFERRED`。三处 schema 同步:`db/dbml/schema.dbml`(权威)→ `db/initdb/01_create_schema.sql`(initdb/demo 路径)→ Flyway `V9`(prod 路径);`Kindergarten`/`Notification` entity + `NotificationStatusEnum` 同步。
- **`QuietHoursService`**:`resolveQuietWindow(kindergartenId)`(`guardianUserId` 参数预留,一期忽略 → 返回园配置);`isWithinQuietHours(instant)` + `nextEndInstant(instant)` —— `Asia/Seoul` 时区、跨午夜窗(22:00–07:00)处理;空/无配置 → 永不静音。
- **`GuardianNotificationService` 改动**:仅 `RESOLVED` 知会类查 quiet_hours —— 落静音则 `status=DEFERRED` + `deferred_until=` 时段结束(Asia/Seoul)、**不 dispatch**;`ESCALATED` 不查 quiet、即时 dispatch(③a 行为不变);非静音 `RESOLVED` 即时 dispatch。
- **`DeferredNotificationScanner`**:主类加 `@EnableScheduling`;`@Scheduled(fixedDelay)` 扫描 `status=DEFERRED AND deferred_until<=now` → 调 `NotificationService.dispatch`(转 SENDING→SENT/FAILED)。`NotificationRepository` 加查询。

Non-goals:

- **每家长 quiet_hours**(解析签名预留 `guardianUserId`,本期只用园级)。
- **ShedLock 多实例去重**(部署是单实例 watchtower CD,`@Scheduled` 不重复触发;记 follow-up)。
- 规则引擎 `notification_rules` opt-in;SMS;前端;③a 的 N1 端到端集成测试(另议)。

## Capabilities

### Modified Capabilities

- `notifications`: ADDED「Quiet-hours deferral of guardian notifications」—— RESOLVED 落静音延后补发、ESCALATED 穿透、定时扫描补发、园级配置、Asia/Seoul。
- `data-platform`: schema 结构演进(V9:`notifications.deferred_until`、`notification_status_enum` 的 `DEFERRED`、`kindergartens.notification_quiet_hours_json`),纳入 `SchemaConsistencyGuardTest` 不变量。

## Impact

- **schema 迁移 V9(high risk)**:三路径(dbml/initdb/Flyway)同步 + PG `ALTER TYPE … ADD VALUE` 的事务约束(加的枚举值不可同事务使用;迁移加值、运行时跨事务用,安全)。
- **产品代码**:`Kindergarten`/`Notification` entity、`NotificationStatusEnum`、新 `QuietHoursService`、`GuardianNotificationService`(改)、新 `DeferredNotificationScanner`、主类 `@EnableScheduling`、`NotificationRepository`(查询)。
- **测试**(Testcontainers):RESOLVED 落静音→DEFERRED+deferred_until(不 dispatch);ESCALATED 落静音→仍即时 SENT(穿透);非静音 RESOLVED→即时;扫描到期 DEFERRED→dispatch SENT;跨午夜窗边界;无园配置→不延后。
- **CI**:复用 `backend-java-tests.yml`;`SchemaConsistencyGuardTest` + `FlywayMigrationSmokeTest`(initdb+baseline 与 fresh-V1 两路径收敛)验证 schema。
- **spec**:`notifications` + `data-platform` delta。
- **⚠️ high risk:含 schema 迁移(V9)。**
