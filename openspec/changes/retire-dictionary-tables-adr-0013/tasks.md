## 0. 落地顺序与 FK 复核(前置)

- [ ] 0.1 复核无外键/seed 交叉引用指向 `menu`/`common_codes`:grep 业务表 DDL 无 FK 指向二表;grep `db/initdb/*.sql` 无 `INSERT ... SELECT ... FROM common_codes`/`menu`(确认可安全删表)
- [ ] 0.2 锁定落地顺序:enum 端点 → 前端切换 → 删后端 CRUD 栈 → Flyway DROP + 删 initdb → 解锁/改测试(先建替代后删表,避免 entity 在表已删时启动校验炸)

## 1. 后端 enum 元数据端点(新)

- [ ] 1.1 写 `EnumsController` 测试:已知 `name`(`gender`/`guardian_relationship`/`teacher_level`/`event_type`/`event_status` 等)回对应 `type.*` 枚举的 code 集;未知 `name` → 404/400;匿名可读(先看失败)
- [ ] 1.2 实现 `GET /api/v1/enums/{name}`(可选 `context=<table>`):`name→enum class` 注册表分发,返回 `[{code, sortOrder?}]`(label 交前端 i18n);不引入 DB
- [ ] 1.3 `SecurityConfig` 白名单加 `GET /api/v1/enums/**`;测试转绿

## 2. 前端 menu 静态配置(替换 menu.api)

- [ ] 2.1 落 menu 静态配置(`Record<UserRole|'ANONYMOUS', MenuItem[]>`,以 `02_menu.sql` 5 菜单 × 角色为蓝本,沿用 `fallbackMenus` 形态)
- [ ] 2.2 `TopBar.tsx` 改读静态配置;删 `menu.api.ts` + `useGetMenusQuery` 用法

## 3. 前端 common_codes → enum + i18n(替换 signup fetch)

- [ ] 3.1 三组(GENDER/GUARDIAN_RELATIONSHIP/teacher level)label 落前端 i18n(以现有 `FALLBACK_*` 韩文为源)
- [ ] 3.2 `useSignupForm.ts`:移除 `fetch /common_codes`,改读 enum 端点取 code + i18n 取 label(`GuardianForm`/`KindergartenForm` 收 props 不变);保留 gender→relationship 的 parentCode 过滤语义
- [ ] 3.3 删 `commonCodes.api.ts`(或改造为指向 `/enums`)

## 4. 删后端 CRUD 栈 + 白名单

- [ ] 4.1 删除 `CommonCode` 全套(Controller/Service/Mapper/Repository/entity/CreateDTO/UpdateDTO/VO)与 `Menu` 全套(Controller/Service/VO)
- [ ] 4.2 `SecurityConfig` 移除 `/api/v1/common_codes/**`、`/api/v1/menus/**` 白名单及相关注释;确认 `PLATFORM_METADATA_WRITE` 无悬挂引用(无则留 follow-up 清理)
- [ ] 4.3 编译通过(确认无残留引用 `CommonCode*`/`Menu*`)

## 5. 删表(schema, BREAKING-运维,维护者批准后部署)

- [ ] 5.1 加 `V10__drop_dictionary_tables.sql`:`DROP TABLE IF EXISTS menu; DROP TABLE IF EXISTS common_codes;`(幂等)
- [ ] 5.2 删 `db/initdb/02_menu.sql`、`03_CommonCode.sql`(fresh/testcontainer 不再建表;seed 是测试 fixture,改后须 `gradle cleanTest`)

## 6. 测试解锁与改写

- [ ] 6.1 去 `FlywayMigrationTest` 的 `@Disabled`,调整 `coreTablesCreatedByV1` 不含两表,验证纯 Flyway + `ddl-auto=validate` 通过
- [ ] 6.2 `SecurityBoundaryIntegrationTest`:`/common_codes`、`/menus` 的 200 断言改退役语义(404),补 `/api/v1/enums/{name}` 匿名 200
- [ ] 6.3 `SchemaConsistencyGuardTest`:断言迁移后 `menu`、`common_codes` 不存在

## 7. 验证收口

- [ ] 7.1 后端 DooD 全套件 `cleanTest test` 全绿(含解锁后的 `FlywayMigrationTest`)
- [ ] 7.2 前端 node:20 lint+build 绿(注意 React19/Next16 lint 严 + lock 还原)
- [ ] 7.3 自检:`/enums/{name}` 各组返回正确 code;`/menus`、`/common_codes` 已 404;纯 Flyway 冷启动无两表且 validate 通过;注册流/导航不依赖后端字典端点
