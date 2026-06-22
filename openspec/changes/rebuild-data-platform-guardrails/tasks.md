## 1. INC-003：loader PII 投影扫描守卫

- [ ] 1.1 写 `LoaderPiiProjectionGuardTest`（backend 纯单测，无 Spring/DB）：解析 `../db/ne4j_kindergartens/*.py` 文本，正则 `\b[A-Za-z_]\w*\.(<forbidden>)\s*=\s*\$[A-Za-z_]` 找 Cypher 属性绑定违例；禁字段集取自 data-platform INC-003 requirement（password_hash/email/phone/address/contact_phone/contact_email/rrn_encrypted/rrn_first6/emergency_contact_*/birth_date/stream_password_*）
- [ ] 1.2 断言 `violations.isEmpty()` **且** 扫到的 loader 文件数 > 0（防空过）；当前 loader CLEAN → 应即绿（若红=发现真实泄露，单列回报，不静默改）
- [ ] 1.3 容器内跑通该测试

## 2. INC-005：MapStruct 每-mapper 未映射 = 编译错误

- [ ] 2.1 清点 22 个 `@Mapper`，逐个加 `unmappedTargetPolicy = ReportingPolicy.ERROR`
- [ ] 2.2 容器内编译，逐个消解未映射 target：**真实漏映射 → 补 `@Mapping`；有意留空 → 显式 `@Mapping(target=..., ignore=true)`**。逐 mapper 对照源/目标字段，勿误用 ignore 掩盖真漏（本 change 主风险点）
- [ ] 2.3 加 1–2 个核心写路径 mapper 的映射往返测试（断言非 ignore 字段映射后非空）
- [ ] 2.4 容器内编译 + 测试全过；确认未改任何映射的业务语义（仅消除未映射，不改既有字段映射）

## 3. schema-digest：schema 源文一致性守卫 + 修漂移

- [ ] 3.1 写 `SchemaConsistencyGuardTest extends BaseIntegrationTest`：对 Flyway 全量迁移后真实 schema 做结构断言（push_subscriptions 存在 / device_tokens·device_platform_enum 不存在；notifications.sent_at·fail_reason 可空、retry_count 有默认；代表性唯一索引/枚举存在）
- [ ] 3.2 加 `initdb/01_create_schema.sql` 与 `V1__initial_baseline.sql` 结构等价断言（规范化比较，容忍注释/空白）
- [ ] 3.3 按测试暴露修复 `db/dbml/schema.dbml` 漂移（已知：`sent_at`/`fail_reason` 应 nullable 对齐 V3；核查 V2/V4/V5/V6 其它项）；修复仅对齐源文，不改运行时 schema。未本期修的项 `log` 出并记 follow-up（no silent cap）
- [ ] 3.4 容器内该测试绿

## 4. Spec 核对与验证（verification-before-completion）

- [ ] 4.1 核对 `specs/rebuild-guardrails/spec.md` delta：三项移出、backlog 清空
- [ ] 4.2 核对 `specs/data-platform/spec.md` delta：INC-005 守卫 + schema 一致性守卫 requirement 与实现一致
- [ ] 4.3 容器内 `gradle:8.7-jdk21` 实跑**全套件**全绿（既有 141 + 新增），留存证据
- [ ] 4.4 范围核对（git diff）：产品改动仅 mapper 注解（消未映射）+ schema 源文对齐；loader/业务行为/运行时 schema 未改
- [ ] 4.5 requesting-code-review；按反馈修正
- [ ] 4.6 合并 develop / push / `/opsx:archive`（用户驱动，含 spec delta sync）

---

> 风险/无高风险迁移：本 change **不含**删除/迁移/schema 破坏性操作（无新 Flyway 迁移、不改运行时 schema）；schema 源文修复是 dbml/initdb 与现有迁移的对齐性文档改动。INC-005 的 22-mapper 改动是主要审查点（误用 ignore 掩盖真漏映射）——见 tasks 2.2。
