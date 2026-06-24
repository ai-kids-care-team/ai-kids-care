## Context

ADR-0013(Accepted 2026-05-29)已裁定 `menu`/`common_codes` 退役但**仅冻结、未实现**。调研(develop)现状:

- **两表只在 initdb,不在 Flyway**:`db/initdb/02_menu.sql`(`menu`,单数)、`03_CommonCode.sql`(`common_codes`,复数)`CREATE TABLE` + seed;Flyway `V1..V9` 均不含二者。故纯 Flyway 冷启动生产(`Dockerfile.prod`)里两表不存在。
- **后端 CRUD 栈仍在(冻结)**:
  - `common_codes`:`CommonCodeController`(`/api/v1/common_codes`,GET list/{id} + POST/PUT/DELETE,写操作 `@PreAuthorize PLATFORM_METADATA_WRITE`)、`CommonCodeService`(Specification 过滤)、`CommonCodeMapper`、`CommonCodeRepository`、`CommonCode` entity、`CommonCodeCreateDTO`/`CommonCodeUpdateDTO`/`CommonCodeVO`。
  - `menu`:`MenuController`(`/api/v1/menus?roleType=`)、`MenuService`(裸 `JdbcTemplate` 查 `menu` 表,无 entity/repo)、`MenuVO`。
  - `SecurityConfig` 白名单含 `GET /api/v1/common_codes/**`、`/api/v1/menus/**`(匿名可读)。
- **替代所需的 Java 枚举已存在**(`com.ai_kids_care.v1.type.*`):`GenderEnum{MALE,FEMALE}`、`RelationshipEnum{FATHER,MOTHER}`、`LevelEnum{DIRECTOR,VICE_DIRECTOR,TEACHER,OTHER}`、`StatusEnum{ACTIVE,PENDING,DISABLED,REJECTED}`、`EventTypeEnum{…}`、`EventStatusEnum{OPEN,…,ESCALATED}` 等 —— enum 端点几乎是"暴露既有枚举",无需新建领域模型。
- **seed 码组**:`common_codes` 有 GENDER、GUARDIAN_RELATIONSHIP、各业务表的 `status`(parent_code='status')、`teachers` 的 `level`、`detection_events` 的 `event_type`/status 等;多表 `status` 同形(ACTIVE/PENDING/DISABLED)。`menu` 5 个菜单(HOME/CCTV_CAMERAS/DETECTION_EVENT/APPRECIATION_LETTER/ANNOUNCEMENTS)× 角色,全为顶层(parent_id 全 NULL),`MenuService` 不 SELECT `component` 列。
- **前端消费点 + 已有兜底**:`TopBar` 用 `useGetMenusQuery`,已有 `fallbackMenus`(正确雏形);`useSignupForm` 直接 `fetch /common_codes` 取 GENDER/GUARDIAN_RELATIONSHIP/teachers 三组,已有 `FALLBACK_GENDER_OPTIONS`/`FALLBACK_RELATIONSHIP_OPTIONS`/`FALLBACK_TEACHER_LEVEL_OPTIONS`(含韩文 label,正可作 i18n 源)。`GuardianForm`/`KindergartenForm` 通过 props 收选项,无需改导入。`types/user-role.ts` 已有 `roleLabels` 静态映射(同形先例)。
- **测试现状**:`FlywayMigrationTest` 已 `@Disabled("ADR-0013 pending: remove legacy CommonCode JPA mapping ...")`,正等本 change;`SecurityBoundaryIntegrationTest` 断言 `/common_codes`、`/menus` 返回 200;`SchemaConsistencyGuardTest`/`db/dbml/schema.dbml` 本就不含两表(它们在 initdb 旁路)。前端无 `*.test.*`。

## Goals / Non-Goals

**Goals:**
- 落地 ADR-0013:平台参照数据脱离 `menu`/`common_codes` 字典表 —— enum 元数据端点 + 前端静态/i18n 供给。
- 删两表(Flyway DROP + 删 initdb 02/03)、删两套 CRUD 栈、调白名单。
- 解除 `FlywayMigrationTest` `@Disabled`,纯 Flyway 冷启动 `ddl-auto=validate` 通过 ⇒ 冷启动生产不再 500。
- 全程 TDD;后端 DooD 全套件 + 前端 node:20 lint/build 收口。

**Non-Goals:**
- 新增可后台增删的码表(与 ADR-0013 方向相反)。
- 改业务表/枚举语义;i18n 体系重构(仅迁移三组 label);多语言扩展。
- 删表以外的 schema 演进。

## Decisions

### D1. 先建替代、后删表(顺序保证不破坏运行态)
落地顺序严格为:**① 建 enum 端点 → ② 前端切静态/enum → ③ 删后端 CRUD 栈 + 白名单 → ④ Flyway DROP + 删 initdb 02/03 → ⑤ 解锁/改测试**。理由:`CommonCode` 是 JPA entity,若先 DROP 表而 entity 仍在,`ddl-auto=validate` 与启动期映射校验会炸;必须先摘除 entity/映射,再删表。前端先切到不依赖端点的静态/enum 来源,删后端端点才不致前端真断(兜底虽在,但要的是干净切换)。

### D2. enum 元数据端点 = 暴露既有 `type.*` 枚举
`GET /api/v1/enums/{name}?context=<table>`:`name` 为逻辑枚举名(如 `gender`/`guardian_relationship`/`teacher_level`/`event_type`/`event_status`/`status`),`context` 仅对多表共用的 `status` 组用于区分来源表(本期 status 同形,`context` 可作可选/透传)。返回 `[{code, sortOrder?}]`(label 交前端 i18n)。由一张 `name→enum class` 的注册表分发;不引入 DB、不引入新枚举。匿名可读,`/api/v1/enums/**` 进 `permitAll` 白名单(替换 `common_codes`/`menus` 两条)。
- **label 归属**:遵 ADR-0013"backend enum metadata + front-end i18n" —— 端点回 code,label 由前端 i18n;现有 `FALLBACK_*` 的韩文(남자/여자/엄마/아빠/원장…)即首版 i18n 文案。
- **替代**:端点直接回韩文 label —— 否决,违背 ADR-0013 的 i18n 分层,且把展示语锁进后端。

### D3. menu = 前端 TS 静态配置
以 `02_menu.sql` 的 5 菜单 × 角色为蓝本,落一份 `Record<UserRole|'ANONYMOUS', MenuItem[]>` 静态配置(沿用 `fallbackMenus` 形态),`TopBar` 直接读;删 `menu.api.ts`/`useGetMenusQuery`、`MenuController`/`MenuService`/`MenuVO`。
- **为什么静态而非 enum 端点**:菜单是前端路由/展示配置(path/icon/role),天然属前端;ADR-0013 也指定 menu→前端静态配置。

### D4. 删表 = Flyway V10 + 删 initdb,且本 change 即"解冻"依据
新增 `V10__drop_dictionary_tables.sql`:`DROP TABLE IF EXISTS menu; DROP TABLE IF EXISTS common_codes;`(幂等)。删 `db/initdb/02_menu.sql`、`03_CommonCode.sql` 使 fresh/testcontainer 不再建表(seed 即集成测试 fixture,改后须 `gradle cleanTest`)。`data-platform` spec 中"no new Flyway migration SHALL target either table"的冻结,正由本 change(ADR-0013 实现)解除 —— spec delta 同步把冻结要求 REMOVE、改 ADD 退役后形态。
- **FK/seed 交叉引用复核**:两表是字典查找,业务表存的是 code 字符串/枚举(非 FK)。apply 时须 grep 确认无 FK 指向二表、无其它 seed 文件 `INSERT ... SELECT FROM common_codes`,再删。

### D5. 测试解锁与改写
- `FlywayMigrationTest`:去 `@Disabled`,调整 `coreTablesCreatedByV1` 不含两表,验证纯 Flyway + `ddl-auto=validate` 通过。
- `SecurityBoundaryIntegrationTest`:删/改 `/common_codes`、`/menus` 的 200 断言为退役语义(端点不存在 → 404),并补 `/api/v1/enums/{name}` 匿名 200。
- `SchemaConsistencyGuardTest`:断言迁移后 `menu`、`common_codes` 不存在。
- 新增 `EnumsController` 测试:已知 `name` 回对应 code 集;未知 `name` → 404/400;匿名可读。

## Risks / Trade-offs

- **删表破坏性(运维)**:`DROP TABLE` 不可逆 → 标 BREAKING-运维,需维护者批准后部署;生产两表本就该不存在(冷启动),删除对预期生产态无影响;dev/test 经 cleanTest 重建。
- **遗漏调用点致运行期 500**:有兜底但要干净切换 → 以 Explore 的消费点清单逐一改;后端全套件 + 前端 build 兜底回归。
- **i18n 覆盖不全**(某 code 无 label)→ 前端 label 取不到时回退显示 code;首版以 `FALLBACK_*` 全量覆盖三组。
- **enum 与 seed 不一致**(seed 有 `device_tokens` status 等孤儿组/`RelationshipEnum` 缺 `OTHER`)→ 端点以**枚举**为准(seed 即将删除);前端用到的 `OTHER` 关系若需,补进 `RelationshipEnum` 或前端常量(apply 时按实际用量定,记 Open Questions)。

## Migration Plan

- **schema**:Flyway `V10`(DROP 两表)随后端镜像滚动;baseline 库执行 DROP,fresh 库因 initdb 不再建表而本就无。**不可回滚到"有表"**(如需回滚,还原镜像 + 保留旧卷;新装则两表本不应存在)。
- **代码**:enum 端点/前端静态配置为新增、向后兼容;删 CRUD 栈与切前端须同批上线(避免前端调已删端点)。
- **回滚**:整体回退后端+前端镜像即可;V10 已执行则两表已删,回滚需配套恢复(故标破坏性、维护者把关)。

## Open Questions

- **`RelationshipEnum` 的 `OTHER`**:前端关系下拉是否实际需要 `OTHER`?需要则补进枚举或前端常量(seed 仅 FATHER/MOTHER)。
- **`/enums` 的 `context` 语义**:多表 `status` 当前同形,`context` 本期可仅透传/忽略;未来若各表 status 分化再启用按表过滤 —— 是否现在就预留参数(倾向预留、本期不分化)。
- **`PLATFORM_METADATA_WRITE` 权限**:`common_codes` 写端点删除后,该 action 是否还有其它用处?若无,清理留 follow-up(不在本 change 强行删权限定义)。
