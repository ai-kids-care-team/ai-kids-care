## 1. 测试↔seed 契约清单（红线，开工第一步）

- [x] 1.1 grep + 通读全部 19 个 `extends BaseIntegrationTest` 测试,逐个记录对 seed 的硬依赖(账号身份、园 id、背景数量、负样本 room、跨租户对照组)
- [x] 1.2 汇成 invariants 清单(不可变红线)→ 交付物 **`seed-test-contract.md`**(A 具体行/值含 admin + child_id=1 的 RRN-hash 隐藏依赖;B 园存在性+背景量;C 不得冲突的 phone/login_id;D FK 完整性;E schema-only 测试)
- [x] 1.3 容器内先跑一遍现有测试,确认**改前全绿**(回归基线)〔`gradle:8.7-jdk21` DooD + testcontainers 实跑全套件 **BUILD SUCCESSFUL in 2m26s / 0 failures**;冒烟 `ContextLoadSmokeTest` 单测亦绿〕

## 2. 重写业务 seed（拓扑序，逐批灌库验证）

- [x] 2.1 按 `seed-rewrite-blueprint.md` 重写 `21..46,88`(4 个 sonnet sub-agent 并行,按 FK 簇):保锚点(admin/child_id=1 原样)+ 每园背景量;净化(room_type 自洽)+ 精简到 3 园小集 + 一条演示链 + 公共空间样本。**7758 → 149 行**
- [x] 2.2 灌库验:`ContextLoadSmokeTest` 验 FK 全过(修 1 处 `notifications.fail_reason` null→''(initdb 期 NOT NULL,V3 前));全套件 **BUILD SUCCESSFUL 2m29s** 验锚点
- [x] 2.3 未改任何测试代码(seed 仅作背景,测试自建 fixture);`30_teachers.level='TEACHER'` 经灌库证为合法 enum

## 3. 清理死数据

- [x] 3.1 删除 `db/initdb/23_device_tokens_seed.sql`(`device_tokens` 已被 Flyway V7 换成 `push_subscriptions`,V7-前死数据)〔已 rm;3.2 SchemaGuard 复绿随末轮全套件一起验〕
- [x] 3.2 `SchemaConsistencyGuardTest` 随全套件复绿(V7 后无 `device_tokens` 表不受影响)

## 4. spec delta

- [x] 4.1 `data-platform` ADDED「Seed dataset quality and test-anchor contract」requirement：规范干净度/自洽/最小性 + 锚点 invariants + prod 不依赖 seed + `device_tokens` seed 缺席;附 scenarios〔`openspec validate` 通过〕

## 5. 验证与收尾（verification-before-completion）

- [x] 5.1 容器内实跑**全套件**全绿:`gradle:8.7-jdk21` DooD + testcontainers,**BUILD SUCCESSFUL in 2m29s / 0 failures**(19 集成测试 + SchemaGuard + FlywayMigrationSmoke + 单元)
- [x] 5.2 范围核对(git status):仅 24 个 `db/initdb` 业务 seed(23 删除)+ `data-platform` spec delta + change 目录;**未动** `01_create_schema`/`24_kindergartens`/`40_ai_models`/`schema.dbml`/参照数据/`BaseIntegrationTest`/java/migration;无 schema 迁移。seed 7758→149 行
- [x] 5.3 code review(**opus** sub-agent,按模型策略 review=opus):**Ready to merge,无 Blocking**;采纳 NB-1(child_id=1 改 ACTIVE/leave_date=null,RRN 锚点不动)、NB-4(三台相机名加园名);NB-3(`99_sync_sequences` 对 V7-dropped `device_tokens` 的 setval = 无害 no-op,在 Non-goal 范围外)记 **follow-up**。改后 `cleanTest` 全套件复绿 2m29s
- [x] 5.4 archive(data-platform spec delta sync)+ commit develop + push(本 change 直接在 develop 上做,无 worktree)

---

> 无 schema 迁移、无 prod 影响(prod 不灌 seed)。本期为 **B-窄**(净化重写),不做 A(fixture 化)。
