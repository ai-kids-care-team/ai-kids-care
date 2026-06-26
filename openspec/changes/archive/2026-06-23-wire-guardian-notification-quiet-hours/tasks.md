## 1. schema 迁移 V9（high risk — 三路径同步）

- [x] 1.1 `db/dbml/schema.dbml`(权威):`kindergartens` 加 `notification_quiet_hours_json`;`notifications` 加 `deferred_until`;`notification_status_enum` 加 `DEFERRED`
- [x] 1.2 ~~改 01_create_schema~~ **不改**:V8 注释 + data-platform spec 确认 01 是 V1 baseline 快照、不可编辑演进;V9 在 fresh-V1 与 initdb+baseline 两路径都 apply,故两路径终态都含新列/枚举值(由 SchemaConsistencyGuard 验)
- [x] 1.3 `V9__quiet_hours_deferral.sql`:`ALTER TYPE … ADD VALUE IF NOT EXISTS 'DEFERRED'` + 两 `ALTER TABLE ADD COLUMN`(事务内 ADD VALUE 在 PG16 灌库通过,无需 transactional=false)
- [x] 1.4 `Kindergarten`+`quietHoursJson`、`Notification`+`deferredUntil`、`NotificationStatusEnum`+`DEFERRED`;`NotificationMapper` 旧 CRUD 写路径加 `deferredUntil` ignore;V9 apply + ddl-validate + SchemaGuard 容器绿 **1m56s**

## 2. QuietHoursService（TDD）

- [x] 2.1 `QuietHoursServiceTest`(纯单元,6 例):同日/跨午夜静音判断、nextEnd 今日/明日、空/null/非法配置不静音、Asia/Seoul 换算 —— 全绿
- [x] 2.2 `QuietHoursService`:`resolveQuietWindow(kgId, guardianUserId 预留)` + `parse` + `isWithinQuietHours` + `nextEndInstant`;`ZoneId.of("Asia/Seoul")`;`QuietWindow.contains` 处理跨午夜;空→不静音

## 3. GuardianNotificationService 延后

- [x] 3.1 `GuardianNotificationService` 注入 `QuietHoursService`;RESOLVED 落静音 → DEFERRED+deferred_until 不 dispatch;ESCALATED 不查 quiet 即时(③a 不变);非静音 RESOLVED 即时;dedupe_key 不变
- [x] 3.2 `GuardianNotificationQuietHoursTest`(动态窗覆盖 now,4 例):RESOLVED 落静音→DEFERRED;ESCALATED 穿透→SENT;非静音/无配置 RESOLVED→SENT —— 全绿

## 4. DeferredNotificationScanner

- [x] 4.1 主类 `@EnableScheduling`;`NotificationRepository.findByStatusAndDeferredUntilLessThanEqual`;`DeferredNotificationScanner.scan()` `@Scheduled(fixedDelayString/initialDelayString,可配)` → best-effort dispatch;test profile 大间隔禁自动扫描
- [x] 4.2 `DeferredNotificationScannerTest`(直接同步调 scan):到期 DEFERRED→SENT;未到期→保持 DEFERRED —— 全绿

## 5. 验证与收尾（verification-before-completion）

- [x] 5.1 容器内 `gradle:8.7-jdk21` DooD `cleanTest test` 全套件全绿:**BUILD SUCCESSFUL in 2m38s**(③b 新增 12 例 + 全回归,含 SchemaConsistencyGuard + FlywayMigrationSmoke);修 ③a `guardianWithoutActiveSubscription` 隔离(共享容器 user121 跨类 subscription 污染→清全部)
- [ ] 5.2 范围核对(git diff):schema 三处(dbml/initdb/V9)+ entity/enum + QuietHoursService + GuardianNotificationService(改)+ DeferredNotificationScanner + @EnableScheduling + repo 查询;未碰规则引擎/SMS/前端/每家长 quiet_hours
- [x] 5.3 code review(**opus** sub-agent):**Ready to merge,无 Blocking**;V9 双路径/initdb 未改/时区跨午夜/延后穿透/scanner 逐项核查通过。采纳 NB-1(`SchemaConsistencyGuardTest` 加 `v9_*` 断言 + enum 成员 helper)、NB-3(两个 ③b 测试 `@AfterEach` 清 user-121 订阅);NB-2(ShedLock 多实例去重)记 memory follow-up。改后全套件复绿 **2m47s**
- [x] 5.4 archive(notifications + data-platform spec delta sync)+ commit develop + push

---

> ⚠️ **high risk:含 schema 迁移 V9**(enum ADD VALUE + 两列,三路径同步)。ESCALATED 行为不变(③a);本期只让 RESOLVED 落静音延后补发。ShedLock 多实例去重记 follow-up(单实例部署)。
