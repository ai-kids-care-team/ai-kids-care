## Why

`menu` 与 `common_codes` 是平台字典表,**ADR-0013(Accepted 2026-05-29)** 已裁定退役:`menu`→前端 TypeScript 静态配置、`common_codes`→后端 enum 元数据端点(`GET /api/v1/enums/{name}?context=<table>`)+ 前端 i18n。`data-platform` spec 把二者冻结为"MUST NOT be extended、no new Flyway migration SHALL target either table、awaiting independent Implementation"。

至今该退役**仅冻结、未实现**:两表只在 `db/initdb/02_menu.sql`、`03_CommonCode.sql` 建,**不在 Flyway**;`FlywayMigrationTest` 因此被 `@Disabled("ADR-0013 pending: remove legacy CommonCode JPA mapping ...")` 挂起;`ddl-auto=validate` 的纯 Flyway 冷启动无法通过。直接后果:**无 seed 生产(`Dockerfile.prod`,Flyway-only)里两表根本不存在**,`GET /api/v1/menus`、`/api/v1/common_codes` 会 500(目前靠前端硬编码兜底 `fallbackMenus`/`FALLBACK_*` 维持注册/导航)。

本 change 落地 ADR-0013,让平台参照数据彻底脱离字典表:删表 + 删 CRUD 栈,改由 enum 元数据端点 + 前端静态配置供给。冷启动生产从此干净(无表、无 500),并解除 `FlywayMigrationTest` 的 `@Disabled`。

## What Changes

- **后端 enum 元数据端点(新)**:新增 `GET /api/v1/enums/{name}?context=<table>`,由既有 `com.ai_kids_care.v1.type` 下的 Java enum(`GenderEnum`/`RelationshipEnum`/`LevelEnum`/`StatusEnum`/`EventTypeEnum`/`EventStatusEnum` 等)直接供值,返回该枚举的 `code` 列表(及可选稳定排序);多表共用的 `status` 组用 `context=<table>` 区分。匿名可读(替换原 `common_codes` 白名单)。
- **前端 menu 静态配置(替换)**:用一份 TS 静态配置(`Record<role, MenuItem[]>`,以现有 `02_menu.sql` 的 5 个菜单 × 角色为蓝本,`fallbackMenus` 已是正确雏形)替换 `menu.api.ts` + `useGetMenusQuery`;`TopBar` 直接读静态配置。
- **前端 common_codes → enum + i18n(替换)**:注册流(`useSignupForm`/GuardianForm/KindergartenForm)的 GENDER / GUARDIAN_RELATIONSHIP / teacher level 三组,从"`fetch /common_codes`"改为"读 enum 端点取 code + 前端 i18n 取 label";现有 `FALLBACK_*` 常量的韩文标签即 i18n 文案来源。
- **删后端 CRUD 栈**:删除 `CommonCode` 全套(Controller/Service/Mapper/Repository/entity/CreateDTO/UpdateDTO/VO)与 `Menu` 全套(Controller/Service/VO);`SecurityConfig` 移除 `/api/v1/common_codes/**`、`/api/v1/menus/**` 白名单(改放 `/api/v1/enums/**`)。
- **删表(schema, BREAKING-运维)**:新增 Flyway `V10__drop_dictionary_tables.sql`(`DROP TABLE IF EXISTS menu, common_codes`),并删除 `db/initdb/02_menu.sql`、`03_CommonCode.sql`(fresh/testcontainer 不再建表)。**需维护者批准后部署。**
- **解锁测试**:去掉 `FlywayMigrationTest` 的 `@Disabled`,使其在纯 Flyway 冷启动上验证 `ddl-auto=validate` 通过;`SecurityBoundaryIntegrationTest` 原断言 `/common_codes`、`/menus` 返回 200 改为退役后语义(404/移除);`SchemaConsistencyGuardTest` 断言两表退役后不存在。

非目标(Non-goals):改动 `common_codes` 之外的业务表/枚举语义;新增字典表式可后台增删的码表(ADR-0013 的方向就是去掉它);i18n 体系大改(仅迁移这三组 label,沿用现有前端文案);多语言新增(仅保持现有韩文 label);删表以外的 schema 演进。

## Capabilities

### Modified Capabilities
- `data-platform`: 移除"字典表冻结待实现(ADR-0013)"要求;新增"平台参照数据由 enum 元数据端点 + 前端静态配置供给,不依赖字典表"要求(`menu`/`common_codes` 表、seed、CRUD 栈均退役)。

## Impact

- 后端:**新增** `EnumsController` + 支撑 service(基于既有 `type.*` 枚举);**删除** `CommonCode`/`Menu` 两套 CRUD(11 个 Java 文件);`SecurityConfig` 白名单调整。
- DB:**Flyway `V10`**(`DROP TABLE` menu + common_codes);删 `db/initdb/02_menu.sql`、`03_CommonCode.sql`(seed 是集成测试 fixture,改后须 `gradle cleanTest`)。
- 前端:删 `menu.api.ts`、`commonCodes.api.ts`(或改造指向 `/enums`);新增 menu 静态配置 + 三组 enum 的 i18n label;改 `TopBar.tsx`、`useSignupForm.ts`(`GuardianForm`/`KindergartenForm` 收 props,无需改导入)。
- 测试:解 `FlywayMigrationTest` `@Disabled`;改 `SecurityBoundaryIntegrationTest`(两端点);扩 `SchemaConsistencyGuardTest`(两表不存在);新增 `/enums` 端点测试;前端无既有测试。docker DooD 后端全套件 + 前端 node:20 lint/build。
- spec:`data-platform` delta(REMOVE 冻结要求 + ADD 参照数据新形态);引用 `Flyway manages production schema evolution` 不修改。
- 运维:`DROP TABLE` 属破坏性 schema 变更,需维护者批准;两表无业务 FK 依赖(字典查找用,非外键),删除对业务数据无影响(apply 时须复核确无 FK/seed 交叉引用)。
