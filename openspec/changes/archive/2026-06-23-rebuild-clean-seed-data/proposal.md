## Why

`db/initdb/21..46,88` 的业务 seed 是早期糊弄出来的 dummy,质量差且**正在主动误导阅读它的人和 agent**:

- **脏 `room_type`**:`room 8` 名叫 `복도1`(走廊1)`room_type` 却是 `식당`(食堂);`room 36` 名叫 `복도1` 却标 `교실`(教室)。`room_type` 是自由字符串而非 enum,数据与名称不自洽。
- **关系基数假象**:`class_room_assignments` 全是「一个 room ↔ 一个 class」的 1:1,掩盖了真实的多对多可能,刚刚就让人误判「教室↔班级」是静态 1:1。
- **死数据**:`23_device_tokens_seed.sql` 仍在,但 `device_tokens` 表已被 Flyway `V7` 换成 `push_subscriptions`(initdb 建表→seed 插入→V7 DROP,数据最终被删),是无意义的 V7-前临时插入。
- **无契约约束**:目前**没有任何 spec/约定**规范 seed 的质量与形状,这正是它能「做得烂」且无人察觉的根因。

这些 seed 不只是 demo 数据——它们同时是**测试 fixture 基底**:19 个继承 `BaseIntegrationTest` 的集成测试把整个 `db/initdb` 目录挂进 testcontainer 当 schema+种子(prod 走 Flyway,**不**依赖 seed)。所以「降噪」既能止住对 agent 的误导,又不触碰生产路径。

经实测权衡,本 change 走 **B-窄**(净化重写,保架构与拓扑)而非 **A**(把测试 fixture 化、彻底解耦 seed):授权测试已被设计成「自建受控数据、seed 仅作背景锚点」(`createRoom()`/`aSeededRoomOtherThan()`/`kindergartenRoomCount()>1`),A 能买到的解耦红利小,却要付「改 `BaseIntegrationTest` + 19 个测试取数 + 重做 `SchemaConsistencyGuard` 的 initdb 腿」的大成本,性价比不及 B-窄。

## What Changes

- **固化「测试↔seed 契约清单」**(开工第一步、重写红线):grep + 通读全部 19 个集成测试,把它们对 seed 的硬依赖列成不可变 invariants。已知锚点(初步,待第一步补全):
  - `admin`(`login_id='admin'`,`user_id=1`,见 `21_users_seed`,`AuthEndpointTest` 重度依赖)及各角色登录账号身份;
  - 园 id `KG ∈ {1,2,3}`(跨租户对照组,`TenantIsolationIntegrationTest` 等);
  - 每园 `room` 数 `> 1`(`kindergartenRoomCount()>1`)且存在「未分配给测试 teacher 的 seed room」负样本(`aSeededRoomOtherThan`);
  - 各园有足量 class / child / camera 作背景。
- **重写业务 seed 为干净小数据集**:在保住上述锚点 + 每园背景数量的前提下,净化数据值(`room_type` 取值一致且与 `name` 自洽、关系基数真实)、按 FK 拓扑序(文件编号即拓扑序)保 id 重写、压到最小够用行数。**不改测试 assert 的语义、不动测试依赖的关系拓扑。**
- **清理 `23_device_tokens_seed.sql`**(V7 死数据)。
- **首次把 seed 契约 normative 化**:`data-platform` 新增一条 requirement,把「seed 是干净、自洽、最小、且满足测试锚点 invariants 的数据集」写成规范,给未来改 seed 的人/agent 立规矩。

Non-goals(本期不做):

- **不** fixture 化、**不**改 `BaseIntegrationTest` 取数架构(那是 A 方案,另议)。
- **不**动 `01_create_schema.sql` / `02_menu.sql` / `03_CommonCode.sql` / `db/dbml/schema.dbml` 等 schema 与平台参照数据;**无 schema 迁移**。
- **不**改测试的断言语义或关系拓扑;**不**把 `room_type` 收敛成 enum(属 schema 改,记 follow-up)。
- 现有 `ai-detection`「detection 表 only seed data」、`data-platform` initdb/schema 治理断言**不改**——B-窄重写后它们仍成立。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities

- `data-platform`: ADDED「Seed dataset quality and test-anchor contract」—— 规范业务 seed 的干净度、自洽性、最小性,以及集成测试依赖的锚点 invariants;明确 prod 不依赖 seed、`device_tokens` seed 应清理。

## Impact

- **数据**:`db/initdb/21..46,88` 的业务 `*_seed.sql` 重写;`23_device_tokens_seed.sql` 删除。不动 `00/01/02/03/99` 等 schema 与参照脚本。
- **测试**:19 个继承 `BaseIntegrationTest` 的集成测试 + `SchemaConsistencyGuardTest` / `FlywayMigrationSmokeTest` 作回归验收网;**不改其逻辑**(除非契约清单暴露某锚点需微调,届时最小化处理并记录)。
- **spec**:`data-platform` delta(随 change)。
- **CI / 部署**:复用既有后端测试工作流;**prod 不灌 seed(`Dockerfile.prod` vanilla postgres + Flyway),零生产影响**;demo/CD 镜像下次重灌即得干净数据。
- **风险**:`initdb` 是 all-or-nothing,FK 不一致会让容器起不来、19 个测试整片红——按拓扑序保 id 重写 + 灌库即验化解(确定性,非偶发)。`AuthEndpointTest`(64 处 seed 引用)是最大单点,靠保留关键账号身份化解。
- **无 schema 迁移、无高风险操作。**
