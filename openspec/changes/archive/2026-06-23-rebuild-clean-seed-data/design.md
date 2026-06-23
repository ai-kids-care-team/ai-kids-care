## Context

降噪 change。已核实(代码 + seed 实测):

- **加载机制**:`BaseIntegrationTest` 用 `withCopyFileToContainer(db/initdb → /docker-entrypoint-initdb.d)`,Postgres 按文件名顺序执行 → 同时建 schema + 灌 seed,并顺带证明 `ddl-auto=validate` 对真实 schema 不漂移。19 个类 `extends BaseIntegrationTest`。
- **测试对 seed 的依赖形态**:授权测试(范本 `TeacherRoomAssignmentAuthorizationIntegrationTest`)**自建**受控数据(`createClass()/createRoom()/createActiveTeacher()/assignClassToRoom()`),seed 只当**背景/负样本**(注释原话:"seeded rooms are genuine-but-unassigned for this principal")。`@Sql/insert/save` 自建 fixture 共 82 处、跨 15 文件 —— 「自建 fixture + seed 作背景」是普遍风格(rebuild-backend-test-suite 那轮留下的好底子)。
- **已知锚点(初步)**:`admin`(`login_id='admin'`,`user_id=1`)、`KG ∈ {1,2,3}`、每园 `room>1`、负样本 room、跨租户对照园;`AuthEndpointTest` 64 处引用 seed 账号身份(最重)。
- **device_tokens**:`01_create_schema` 建表 → `23_device_tokens_seed` 插入 → Flyway `V7` DROP。seed 23 是 V7-前死数据(**不报错**,纯无用)。
- **spec 现状**:无任何 seed 质量契约;`ai-detection`「detection 表 only seed data」(spec.md 现行 line 138-141 / 194-197)在 B-窄重写后**仍成立**(seed 仍在,只是更干净)→ 不改。`data-platform` 的 initdb/schema 治理断言只约束 `01_create_schema`/schema.dbml,不涉业务 seed → 不改。
- **room_type**:自由字符串(非 enum),seed 中脏(name 与 type 不一致)。
- **prod 不依赖 seed**:`docker-compose.prod.yml` 用 `Dockerfile.prod`(vanilla postgres,无 initdb seed),schema 全靠 Flyway。

## Goals / Non-Goals

**Goals:** 把业务 seed 重写成干净、自洽、最小、满足测试锚点契约的数据集;清死数据;首次把 seed 契约写进 spec。消除对后续 agent/人的误导。

**Non-Goals:** fixture 化(A 方案);改 `BaseIntegrationTest` 取数;动 schema / `01_create_schema` / `schema.dbml` / 参照数据;改测试断言语义或关系拓扑;`room_type` enum 化;改既有 detection/initdb spec 断言。

## Decisions

### D1：开工第一步产出「测试↔seed 契约清单」(不可变红线)
grep + 通读全部 19 个 `extends BaseIntegrationTest` 测试,列出每个测试对 seed 的硬依赖(账号身份、园 id、背景数量、负样本、跨租户对照组),汇成一份 invariants 清单。后续重写**必须**逐条满足;清单是验收基线,也是 spec delta 的素材来源。

### D2：重写策略 = 保锚点 + 拓扑序保 id + 净化值 + 最小行数
- **保锚点**:D1 清单里的 id / 身份 / 背景数量逐条保留(尤其 `admin=user_id=1`、`KG∈{1,2,3}`、每园 `room>1`、负样本 room)。
- **拓扑序保 id**:按文件编号(已是 FK 拓扑序:users→kindergartens→classes→rooms→cameras→memberships→roles→child_class→class_room→child_guardian→room_camera→sessions→events→reviews→...)逐表重写,引用既有 id,保证 `initdb` 灌库 FK 全过。
- **净化值**:`room_type` 取一致且与 `name` 自洽的取值;关系基数反映真实(允许出现合理的多对多/1:N,但**不破坏**测试依赖的拓扑);去掉无意义/矛盾字段值。
- **最小行数**:压到「够测试背景 + 够 demo 展示」的最小量级,行数目标在 D1 后按契约定。

### D3：清理 device_tokens seed
删 `23_device_tokens_seed.sql`(V7 死数据)。`SchemaConsistencyGuardTest` 断言「V7 后无 device_tokens 表」不受影响(那是 schema 层;删的是 V7-前的插入脚本)。

### D4：spec —— data-platform ADDED「seed 契约」
新增 requirement 把 D1 的 invariants + 干净度/自洽/最小性规范化,并声明 prod 不依赖 seed、`device_tokens` seed 应缺席。这是本 change 给「防后续误导」立的长期护栏。

### D5：验收 = initdb 灌库通过 + 现成测试网全绿
`initdb` all-or-nothing 灌库通过即证 FK 自洽;19 个集成测试 + `SchemaConsistencyGuardTest` + `FlywayMigrationSmokeTest` 全绿即证锚点未破。容器内实跑、留证据。

## Risks / Trade-offs

- **[initdb all-or-nothing]** FK 不一致 → 容器起不来 → 测试整片红。化解:拓扑序、保 id、灌一次库即确定性暴露(非偶发「打地鼠」)。
- **[AuthEndpointTest 重度依赖]** 64 处引用 seed 账号身份。化解:D1 先精确列出它依赖的账号集,重写时全保留。
- **[「净化」手滑破锚点]** 例如把某园 room 降到 1、破坏 `room>1`。化解:D1 清单作红线 + 测试网兜底。
- **[B-窄不解决根脆弱]** 「测试钉死共享 seed」这个隐式耦合仍在(A 才根治)。本期接受;若将来频繁因 seed 震荡测试,再评估 A。记 follow-up。
- **[估算]** B-窄一次性顺利落地 ~85%(配 D1 契约清单可到 ~95%);失败模式是「一次性调通 FK + 锚点」,非「持续返工」。

## Migration Plan

1. **契约清单**:grep + 读全 19 测试 → invariants 清单(D1)。
2. **基线确认**:容器内先跑一遍现有测试,确认改前全绿(基线)。
3. **逐表重写 seed**(拓扑序):每改一批灌库 + 跑测试,增量验 FK 与锚点(D2)。
4. **清 device_tokens seed**(D3)。
5. **spec delta**:data-platform ADDED seed 契约(D4)。
6. **收尾**:容器内全套件 + SchemaGuard 全绿留证;范围核对(仅 seed + spec,无 schema 迁移);code review;archive + 合 develop。
- **回滚**:`git` 还原 seed 文件;无 schema、无 prod 影响。

## Open Questions

- `room_type` 是否顺手收敛成 enum?倾向**不**(属 schema 改,超出 B-窄;记 follow-up)。
- 重写后每园行数目标量级(几班/房/孩子/事件)?D1 后按「最小够用」定。
- 是否给 demo 展示保留少量「公共空间检测事件」样本(操场/走廊),以便前端/演示覆盖公共空间路径?apply 时定。
