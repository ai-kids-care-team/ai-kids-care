---
ADR: ADR-0013
title: "ADR-0013: menu / common_codes 字典表的治理与去留"
status: Accepted
date: 2026-05-29
deciders: 维护者（2026-05-29 Accept；menu → C 静态；common_codes → β 后端枚举元数据端点 + 前端 i18n）
---

# ADR-0013: menu / common_codes 字典表的治理与去留

> **前瞻提案**。维护者于 2026-05-29 指出：`menu`/`common_codes` **非原作者设计**，命名/逻辑可能有问题，需复审"是否有更改设计的必要性"（OQ-DATA-4）。
>
> 第二轮（2026-05-29）：**`menu` 方向已定 → C 静态**；第三轮（2026-05-29）：**`common_codes` 方向已定 → β（后端枚举元数据端点 + 前端 i18n）**。本 ADR 现已 Accept。

## 状态（Status）

Accepted（2026-05-29 签署）
- `menu` → **C（静态化）**；落地待 Implementation。
- `common_codes` → **β（后端枚举元数据端点 + 前端 i18n）**；落地待 Implementation。

## 背景（Context）

✅ 两表在核心 DBML（`01_create_schema.sql` 的 28 张表）**之外**，由独立脚本创建：
- `02_menu.sql:3` `CREATE TABLE menu`（**单数**）；
- `03_CommonCode.sql:1` `CREATE TABLE common_codes`（**复数**）。

✅ 命名风格与核心表不一致；本知识库此前一直**误写为 `common_code`（单数）**。

✅ 实体层不对称：`common_codes` 有 `CommonCode` 实体，`menu` 有 `MenuController` 但**无 `Menu` 实体**。

✅ **`menu` 表实际状态**（2026-05-29 核查代码与种子）：
- 自引用树（`parent_id` → `menu(menu_id)`），含 `menu_name`/`menu_key`/`path`/`component`/`icon`/`role_type`/`sort_order`/`is_active`。
- **无 `kindergarten_id`**——全局共享，不分租户。
- `menu_name` 为**韩语字面量硬编码**（"홈"/"대시보드"/…），不走 i18n 消息键（与 [ADR-0008](ADR-0008-language-governance.md) 的"消息键中立"路线不一致）。
- `role_type` 一行一角色：同一菜单条目在 7 个角色（含 `ALL`/`ANONYMOUS`）上**横向展开**为 N 行。
- `path`/`component` **指向具体的前端路由与组件名**（如 `/cctvCameras` ↔ `CctvCamerasPage`），与前端代码紧耦合。
- `MenuController` 仅暴露 `GET /api/v1/menus?roleType=...`（`MenuController.java:25-29`），**无写入 API**。
- 前端 `menu.api.ts` 调用 `/menus?roleType=...`，代码注释："**메뉴는 자주 바뀌지 않음**"，`keepUnusedDataFor: 86400`（24 小时缓存）。

→ 当前形态实质是"DB 托管的只读静态表"，双倍成本、单倍收益。

## 决策（Decision）

### `menu` → **C（静态化）**（方向已确认，2026-05-29）

将 `menu` 数据从数据库移除，**改为前端配置承载**，按当前已观测的角色驱动逻辑过滤。理由（详见下节"说明"，本节只列结论）：
- 无写 API + 无 tenant + i18n 不合规 + 与前端代码强耦合 + 前端已视其为"不常变"——所有信号一致指向 C。
- 移除一处运行时故障点；消除"前端代码 ↔ seed"双源同步问题；天然与 [ADR-0008](ADR-0008-language-governance.md) 的 i18n 键化对齐。

落地范围（**留待 Implementation，本 ADR 不实施**）：
1. **前端**：在 `frontend/src/config/` 新增 `menu.ts`（或 `menu.json`），用 TypeScript 类型承载菜单树；按 `role` 过滤；菜单文案改走 i18n 消息键（[ADR-0008](ADR-0008-language-governance.md) 轨道，`ko` 必出货）。
2. **前端 API 层**：删除 `frontend/src/services/apis/menu.api.ts`；改造调用方（角色化导航组件）从本地配置读取。
3. **后端**：移除 `MenuController`、`MenuService`、`MenuVO`、相关 mapper/repository（如有）。
4. **数据库**：从 initdb 移除 `02_menu.sql`（开发/演示环境直接消失；生产无 `menu` 表故无影响——配合 [ADR-0012](ADR-0012-production-data-lifecycle.md) 的迁移基线）。
5. **文档同步**：`features.md §6`、`rest-endpoints.md`、`frontend-architecture.md §5`、`data-architecture.md` 移除 menu 相关条目并指向本 ADR 作为历史决策记录。

### `common_codes` → **β（后端枚举元数据端点 + 前端 i18n）**（方向已确认，2026-05-29）

将 `common_codes` **从 DB 移除**，由两个职责分离的子系统接管：
1. **后端**通过 `GET /api/v1/enums/{name}?context=<table>` 暴露 Java enum 元数据（值 + i18n labelKey），从 `type/` 包反射；
2. **前端**用 labelKey 在本地 i18n 资源文件渲染显示名（`ko` 必出货，按 [ADR-0008](ADR-0008-language-governance.md)）。

理由（详见下节"说明"，本节只列结论）：
- 现状 `common_codes` **只贡献"按上下文的 i18n 标签"**——这是 [ADR-0008](ADR-0008-language-governance.md) i18n 轨道的天然职责。
- 与 PG enum 形成的"两份事实"取消——值域权威单一回到 Java enum / PG enum。
- API 层有 CRUD 但**前端 UI 层无写入**——与 menu 同型的"双倍成本单倍收益"，无运行时编辑标签的真实承诺。

落地范围（**留待 Implementation，本 ADR 不实施**）：
1. **后端**：新增 `EnumMetadataController`（`GET /api/v1/enums/{name}?context=<table>`），从 `com.ai_kids_care.v1.type.*` 包反射返回 `[{value, labelKey}]`（labelKey 形如 `enum.<name>.<context>.<value>`；无上下文差异时退化为 `enum.<name>.<value>`）。移除 `CommonCodeController` / `Service` / `Mapper` / `Repository` / `CommonCode` 实体 / `CommonCodeCreateDTO` / `CommonCodeUpdateDTO` / `CommonCodeVO`（共 **8 个后端文件**）。
2. **前端**：在 `frontend/locales/<lang>/enum.json` 新增 labelKey → 文案映射；重写 `commonCodes.api.ts` 为 `enums.api.ts`（取 enum 元数据，缓存策略类比 menu）；调用 `getParentCommonCodeList` / `getCommonCodes` 的页面改为 `useEnum('relationship', { context: 'guardians', filterBy: { gender: ... } })` 形态。
3. **数据库**：从 initdb 移除 `03_CommonCode.sql`（演示环境随之消失；生产无 `common_codes` 表故无影响——配合 [ADR-0012](ADR-0012-production-data-lifecycle.md) 的迁移基线）。
4. **CI 校验（新护栏）**：增加"PG enum 取值 = Java enum 取值"的对照测试，防止两者漂移——这是把 enum 权威收回单一来源后的护栏，否则隐性回到"两份事实"。
5. **文档同步**：`features.md §6`、`rest-endpoints.md`、`data-architecture.md` 移除 `common_codes` 相关条目并指向本 ADR；`product/glossary.md` 增补"按上下文显示名"统一由 i18n 键提供的说明。

## 说明：menu 数据"静态 vs 运行时可配"（应维护者要求，已采纳静态）

> 本节保留作为决策依据；维护者已据此选定 C。

### 现状给出的强信号

仅基于已核查事实，**`menu` 目前的实际形态是"DB 托管的只读静态表"**——同时承担静态与运行时的成本，却只享受静态语义：

| 维度 | 现状 | 解读 |
| --- | --- | --- |
| 写路径 | 无写 API；仅 seed/直改 DB | **不是真正"运行时可配"** |
| 读路径 | DB → REST → 前端缓存 24h | 多一跳 HTTP + 一次 DB 查询 + 引入故障模式 |
| 与前端耦合 | `path`/`component` = 前端路由/组件名 | **强耦合**：新增前端页 = 改前端 + 改 `02_menu.sql` 两处 |
| 多租户 | 无 `kindergarten_id` | 全局菜单；无法按园定制 |
| i18n | 硬编码韩语字面量 | 违反 [ADR-0008](ADR-0008-language-governance.md) |
| 角色编码 | (menu × role) 笛卡尔展开 | 角色增加 → 行数线性膨胀 |
| 一致性 | 跨环境靠手工保证 seed 同步 | 静态天生跨环境一致 |

### 三步决策树（已据此选定 C）

1. 未来 12–24 个月内，是否有非开发人员在线编辑菜单的需求？→ 不会。
2. 是否需要"不同 kindergarten 看到不同菜单"？→ 不需要。
3. 菜单变更频率是否显著高于前端发布频率？→ 否（前端注释已自证）。
→ **C 静态**。

### 静态方案细则

- 配置载体：TypeScript 类型化常量（首选）或 JSON。
- 角色过滤：保留 `roleType` 维度，避免 (menu × role) 笛卡尔展开——单条菜单声明可见角色集合（如 `roles: ['ALL']` / `roles: ['KINDERGARTEN_ADMIN', 'TEACHER']`），运行时按当前用户角色过滤。
- i18n：`label` 改为消息键（如 `menu.home`、`menu.cctvCameras`），实际文案在 `frontend/locales/` 资源文件中按 locale 提供（`ko` 必出货，遵循 [ADR-0008](ADR-0008-language-governance.md)）。
- "无需发布即可关停菜单项"的运维杠杆：用 feature flag（环境变量/远程配置）替代，需要时再引入。

## 说明：common_codes 评估（已据此选定 β）

> 本节平行于上面的 menu 说明，对 `common_codes` 做事实型评估。维护者已据此选定 **β（后端枚举元数据端点 + 前端 i18n）**。

### 现状已核查事实（2026-05-29）

| 维度 | 现状 |
| --- | --- |
| Schema | `(parent_code, code_group, code, code_name, sort_order, is_active, timestamps)`；`parent_code` 是**字符串非 FK**（informal 引用）；**无 `kindergarten_id`**；**无 `UNIQUE(parent_code, code_group, code)` 约束**（seed 用 `WHERE NOT EXISTS` 实现幂等） |
| Backend | **完整 CRUD**（GET 列表/单条、POST、PUT、DELETE）；有 `CommonCode` 实体、Service、Mapper、Repository、DTO×2、VO（8 个相关文件，完整 codegen 产物） |
| Frontend | `commonCodes.api.ts` **仅有 GET 类封装**（`getCommonCodes`、`getParentCommonCodeList`）——**没有写入封装**；含防御性 snake_case ↔ camelCase 双重归一化 |
| 多租户 | 无（全局） |
| i18n 兼容 | 标签**直接以韩语字面量入表**——不走 [ADR-0008](ADR-0008-language-governance.md) 的"消息键中立 + locale 资源文件"轨道 |

### Seed 内容的实质（关键发现）

逐条核对 `03_CommonCode.sql` 的 5 个 INSERT 块，**全部 code 值都是已有 PG enum 的成员**——`common_codes` 不引入任何新的"合法值"，它只贡献"显示名"：

| common_codes 维度 | 对应 PG enum | 实际承担 |
| --- | --- | --- |
| `code_group='GENDER'` (MALE/FEMALE) | `gender_enum` | 性别显示名 |
| `code_group='GUARDIAN_RELATIONSHIP'`（按 parent_code=MALE/FEMALE 过滤） | `relationship_enum` | 亲子关系下拉（按性别过滤） |
| `parent_code='status'` × `code_group=<table>` × ACTIVE/PENDING/DISABLED | `status_enum` | **按表上下文的状态显示名**（如 `kindergartens.ACTIVE='운영'`、`children.ACTIVE='재원'`、`teachers.ACTIVE='재직'`） |
| `parent_code='event_type'` × `code_group='detection_events'` × 13 类 | `event_type_enum` | 事件类型显示名 |
| `parent_code='level'` × `code_group='teachers'` × DIRECTOR/... | `level_enum` | 教师职级显示名 |

→ **`common_codes` 实际承担的功能 = "按上下文的 enum 标签"（≈ contextual i18n table）**，不是"运行时可扩展的值域字典"。

### 核心张力

| 问题 | 现状 |
| --- | --- |
| 值域（什么值合法）的真相 | **PG enum**（schema 层） |
| 显示名的真相 | **`common_codes`** 表（DB 层） |
| 这两份真相是否同步 | **不强制**——seed 手工维护；加 enum 值后若不更新 seed，展示缺失，无 CI 校验 |
| 显示名是否走 i18n 轨道 | **不走** |
| 跨租户定制 | **不支持**（无 `kindergarten_id`） |
| 真正的运行时可配 | **API 层有，UI 层无**（前端无写入封装）——与 menu **同型**的"双倍成本单倍收益" |

→ 与 menu 的根本差异：menu 是路由结构（含逻辑），common_codes 是 i18n 标签（含字符串）。menu 走静态后落到前端代码；**common_codes 走静态后落到前端 i18n 资源文件——恰好是 [ADR-0008](ADR-0008-language-governance.md) 已经为它准备好的轨道**。

### 候选方案

- **α 前端 i18n 化（移到 `locales/<lang>/*.json`）**
  - 标签移入前端 i18n 资源文件，键如 `enum.status.kindergartens.ACTIVE = "운영"`。
  - 删除 `common_codes` 表 + 全部 8 个后端文件 + 前端 `commonCodes.api.ts`。
  - 前端**硬编码 enum 值**（或维护一份共享 TS 常量同步后端）。

- **β 后端枚举元数据端点 + 前端 i18n**
  - 后端新增极小端点 `GET /api/v1/enums/{name}?context=<table>`，**从 Java enum 反射**返回 `[{value, labelKey}]`，labelKey 形如 `enum.status.kindergartens.ACTIVE`。
  - 前端用 value + labelKey 渲染（labelKey 解到本地 i18n）。
  - 删除 `common_codes` 表 + CRUD 栈。
  - **enum 值的权威保留在后端**：添加新 enum 值时前端零代码改动；标签走 i18n 轨道。

- **γ 保留并硬化**
  - 加 `UNIQUE(parent_code, code_group, code)` 约束；决定是否加 `kindergarten_id`；加变更审计、缓存失效、写权限；加 **CI 校验**（PG enum 取值 ⊆ common_codes 取值）；并补齐前端 admin UI。
  - 把潜在的"运行时可配"**真正落地**，承认它是与 i18n 轨道并存的另一种治理方式。

- **δ 保留现状，仅显式记录**
  - 零破坏；接受"显示名脱 i18n 轨道、与 PG enum 漂移、命名不一致"为长期债。

### 三步决策树

1. **未来 12–24 个月内，是否有非开发人员/运营在线编辑标签的实际需求？**
   - 没有 → 排除 γ（投入不值）。
2. **是否需要按 `kindergarten_id` 定制标签？**（不同园显示不同状态文案？）
   - 不需要 → 进一步排除 γ。
3. **后端是否要保留"枚举值域的权威"？**（"哪些 status 合法由后端说了算"，前端不要硬编码值）
   - 要 → **β**（最佳契合）
   - 不要 → **α**（更轻，但前端硬编码 enum 值）

### 中立判断（仅基于事实，**不替你拍板**）

🔶 **基于已核查事实**：
- `common_codes` **实际只贡献"按上下文的 i18n 标签"**——与 [ADR-0008](ADR-0008-language-governance.md) 的 i18n 轨道完全重叠。
- API 层有 CRUD 但**前端 UI 层无写入**——与 menu 同型的"双倍成本单倍收益"。
- 值域与 PG enum **形成两份事实，无同步约束**——隐性技术债（加 enum 值时易遗漏 seed，至少 [OQ-DATA-2](../../modernization/open-questions.md) `relationship_enum` 不足的扩展会立刻撞上这个问题）。

**已选 β（2026-05-29 维护者采纳）**——一次解决三个问题：
1. 让 enum 值的权威单一回到后端（消除两份事实）；
2. 让标签走 i18n 轨道（对齐 [ADR-0008](ADR-0008-language-governance.md)，自动支持未来非 `ko` 出货语言）；
3. 删除整套 common_codes CRUD 栈（减少 8 个文件 + 一张表 + 多次 DB 跳）。

α 比 β 更轻，但代价是把 enum 值列表也搬到前端（后端 enum 改动需前端同步）；当 enum 集合稳定且单端时 α 也成立。

γ 仅当**确有"非开发者运行时改标签"或"按园定制标签"**的承诺时才值得。

δ 是过渡态，不解决问题但零破坏。

→ 与 menu 选 C 的逻辑同源——倾向"移出 DB、让正确的子系统接管"。

## 后果（Consequences）

针对 `menu` 选定的 **C 静态**：
- **正面**：
  - 单一真实来源（前端配置）；类型安全；原子部署；天然 i18n 键化；移除一处运行时故障点；消除双源同步债。
  - 与 [ADR-0008](ADR-0008-language-governance.md) i18n 轨道直接对齐；与 [ADR-0005](ADR-0005-frontend-static-export.md) 静态导出形态一致（菜单也回归静态）。
  - 减少后端样板：`MenuController`/`MenuService`/`MenuVO`/`02_menu.sql` 整体删除。
- **负面 / 代价**：
  - 调整菜单需走前端发布流程（前端发布本就低频，影响有限）。
  - 失去"改 DB 即生效"的运维路径——可被 feature flag 替代但未提供。
  - 一次性破坏 `/menus` 端点契约（前端同步改造可保证不影响最终用户）。
- **影响范围**：`db/initdb/02_menu.sql`（移除）、`MenuController`/`MenuService`/`MenuVO`（移除）、`frontend/src/services/apis/menu.api.ts`（移除）、`frontend/src/config/menu.ts`（新增）、`frontend/locales/`（新增菜单消息键）、`features.md`/`rest-endpoints.md`/`frontend-architecture.md`/`data-architecture.md`（更新）。

针对 `common_codes` 选定的 **β（后端枚举元数据端点 + 前端 i18n）**：
- **正面**：
  - 消除 PG enum / `common_codes` 两份事实；值域权威单一回到后端 Java enum。
  - 标签走 i18n 轨道，对齐 [ADR-0008](ADR-0008-language-governance.md)；天然支持未来非 `ko` 出货语言。
  - 移除 1 张表 + 8 个后端文件 + 1 个前端 API 模块 + 多次 DB 跳。
  - CI 校验"PG enum = Java enum"作为护栏，防止漂移。
- **负面 / 代价**：
  - 新增极小后端端点（`/api/v1/enums/{name}`）+ Java enum 反射机制（轻量）。
  - 前端 i18n 资源需补全 `ko` 文案；现有调用方需迁移到新 hook（一次性改造）。
  - 失去"改 DB 即生效"的标签编辑路径——与 menu 同：可被 feature flag 替代，无运营承诺暂不引入。
  - 一次性破坏 `/api/v1/common_codes` 端点契约（前端同步改造保证不影响最终用户）。
- **影响范围**：`db/initdb/03_CommonCode.sql`（移除）、`CommonCodeController`/`Service`/`Mapper`/`Repository`/`CommonCode` 实体/`CommonCodeCreateDTO`/`CommonCodeUpdateDTO`/`CommonCodeVO`（移除）、`frontend/src/services/apis/commonCodes.api.ts`（重写为 enum 取数）、`frontend/locales/`（新增枚举消息键）、`features.md`/`rest-endpoints.md`/`data-architecture.md`/`glossary.md`（更新）。

## 考虑过的备选（Alternatives Considered）

### menu

- **A 纳入统一治理（强化为真正的运行时可配）**：补 `Menu` 实体 + 增删改 API + i18n 键 + `kindergarten_id` + 变更审计 + 缓存失效——~5 倍现状的工程投入。在维护者明确无"非开发人员在线编辑"需求的前提下，性价比不足。
- **B 显式独立子系统（保留现状仅文档化）**：零破坏但长期保留命名/结构债；过渡价值大于终态价值，最终仍需走向 A 或 C。
- **维持现状且不记录**：否决（文档与实物已偏差，至少需 B）。

### common_codes

- **α 纯前端 i18n 化（无后端 enum 端点）**：更轻，但前端**硬编码 enum 值**；后端 enum 改动需前端同步。本系统 enum 仍会扩展（如 [OQ-DATA-2](../../modernization/open-questions.md) `relationship_enum` 待扩），β 比 α 更稳。
- **γ 保留并硬化（加 UNIQUE / kindergarten_id / 审计 / admin UI / CI 校验）**：仅当**确有**运行时编辑标签或按园定制标签的承诺时值得；当前无此承诺。
- **δ 保留现状仅文档化**：零破坏；不解决"两份事实"与"绕过 i18n 轨道"两个根本问题。

## 关联（References）

- [data-architecture.md §5](../../architecture/data-architecture.md)、[product/features.md](../../product/features.md)、[frontend-architecture.md §5](../../architecture/frontend-architecture.md)、[ADR-0008](ADR-0008-language-governance.md)、[ADR-0005](ADR-0005-frontend-static-export.md)、[ADR-0012](ADR-0012-production-data-lifecycle.md)（生产无需迁移 `menu`）、[open-questions.md](../../modernization/open-questions.md)（OQ-DATA-4）。
- 代码：`db/initdb/02_menu.sql`、`db/initdb/03_CommonCode.sql`、`backend/.../controller/MenuController.java:25-29`、`entity/CommonCode.java`（无 `Menu` 实体）、`frontend/src/services/apis/menu.api.ts`。
