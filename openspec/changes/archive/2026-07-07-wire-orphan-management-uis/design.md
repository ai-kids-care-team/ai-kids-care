## Context

三角度分析确认两处孤儿功能：儿童关系图（`app/graph/page.tsx` + `ChildGraphViewer` + `graph.api.ts` + `GraphController` 全就绪，但 `menu.ts` 五角色皆无入口、只能敲 URL + 手输 childId）与 AI 模型台账（`AiModelController` 完整 CRUD gate 到 `PLATFORM_METADATA_*`，前端零消费者）。后端均已就绪且契约冻结——本变更纯前端接线。依赖：④ DEBT 已落 develop，`useCrudResource` 可复用（UX-01 台账走薄封装，不新增第 6 份重复）。

## Goals / Non-Goals

**Goals:**
- 两个已就绪后端能力从「敲 URL / 无入口」变为「导航可达」，且 UX-02 去掉「手输原始主键」。
- 零后端/契约/schema 变更；纯前端接线到既有冻结端点。

**Non-Goals:**
- 不改后端；UX-01 仅元数据台账（不含训练/权重/绑定生效）；不给 GUARDIAN 开图入口；不新增姓名搜索后端端点。

## Decisions

### D1 — menu.ts 入口与角色
- GRAPH 入口加到 `KINDERGARTEN_ADMIN` + `TEACHER`（园所内），**不加 GUARDIAN**（关系图跨儿童属敏感）。
- AI 모델 관리 入口加到 `PLATFORM_IT_ADMIN`（+ `SUPERADMIN` 若其菜单独立）。
- 沿用 `MENU_BY_ROLE` 既有结构，仅增条目，不改渲染。

### D2 — UX-02 去 raw-id 入口
把 `ChildGraphViewer` 的「手输数字 childId」改为**从既有儿童名册/列表选择**带入 `childId`（复用现有 child 列表数据源，零新后端）。教师侧沿用既有 teacher-centric 查询。**决策：** 优先从既有承载儿童列表的页面/组件提供「查看关系图」跳转（携带 childId），图页仍保留可选的显式选择器作为直达入口；不引入全局姓名模糊搜索（Non-goal）。实现前先定位现有 child 列表 API/组件作为 childId 来源。

### D3 — aiModels.api.ts 客户端与台账页
- `aiModels.api.ts` 消费既有 `GET/POST/PUT/DELETE /api/v1/ai_models`，**HTTP 客户端与 CSRF 注入沿用 adminOperations 同侪**（classes/rooms/cameraStreams 用哪个 client 就用哪个，保持一致；实现前 grep 确认）。
- 台账页用 **④ 的 `useCrudResource`** 薄封装（传入 aiModels 的 list/create/update/delete + 文案），页面结构对齐既有 adminOperations 管理页（列表 + 新增/编辑表单 + 停用）。字段仅 `name`/`version`/`status`（`status` 用既有 `StatusEnum`，label 走前端 i18n）。

### D4 — 停用语义
UX-01「停用」= 调 `PUT` 把 `status` 置为停用态（若 `StatusEnum` 有 INACTIVE/DISABLED 值）还是 `DELETE`？**决策：** 台账管理默认用 `DELETE` 走既有 delete 端点（controller 已暴露）；若产品语义是「软停用」则改 `PUT status`。实现前确认 `StatusEnum` 取值与 `deleteAiModel` 语义（硬删 vs 软删），对齐既有 adminOperations 删除交互（`window.confirm`）。

## Risks / Trade-offs

- [D2 现有 child 列表若无合适承载点，去-raw-id 入口成本上升] → 实现前定位数据源；若确无，则至少加菜单入口 + 图页保留选择器（分步交付），并在收口如实标注。
- [D3 客户端选择不一致引入第二种 CSRF 路径] → 强制对齐 adminOperations 同侪 client，不自创。
- [关系图对 TEACHER 可见性] → 后端租户隔离已在 Cypher 强制，前端仅加入口不放宽后端；TEACHER 只能看本园所（既有约束不变）。

## Migration Plan

无 schema / 无后端 / 无数据迁移。纯前端新增 + 菜单条目。回滚 = revert 前端提交。

## Open Questions

- D4：`ai_models` 停用是硬 `DELETE` 还是软 `PUT status`？取决于 `StatusEnum` 是否含停用态与产品意图——实现前读码/必要时问维护者。
- D2：是否已有合适的儿童列表承载「查看关系图」跳转？无则退化为「菜单入口 + 图页选择器」分步交付。
