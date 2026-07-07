## Why

2026-07-07 三角度分析（experience）对抗式验证确认两处「后端能力已就绪，但前端无可达入口」的孤儿功能：(1) 儿童关系图（`app/graph` + `GraphController` 全就绪）**任何角色菜单都无入口**，只能敲 URL 且要手输原始数字 childId；(2) AI 模型台账（`AiModelController` 完整 CRUD，gate 到平台管理员）**零前端页面/菜单/api 消费者**。两者都是「已建未接线」——平台承诺的功能在界面上办不成。本变更把它们接到可达 UI，**不改任何后端**（纯前端接线到既有端点）。

## What Changes

- **UX-02 关系图可达性**：在 `KINDERGARTEN_ADMIN` / `TEACHER` 菜单加「관계 그래프」入口；把「手输原始数字 ID」改为**从既有儿童名册/列表选择带入 childId**（复用现有 child 列表 API，零新后端），消除「用户须凭空知道内部主键」。教师侧沿用既有 teacher-centric graph 查询。
- **UX-01 AI 模型台账页（范围 A）**：新增 `aiModels.api.ts`（消费既有 `GET/POST/PUT/DELETE /api/v1/ai_models`）+ 平台管理员「AI 모델 관리」页（列表/新增/编辑/停用，**纯元数据 name/version/status**）+ `PLATFORM_IT_ADMIN`（含 `SUPERADMIN`）菜单入口。**复用 ④ 已抽的 `useCrudResource`**（薄封装，不新增第 6 份重复）。范围**仅元数据台账**——不含训练触发/权重上传/绑定生效（那是独立 follow-up）。

均**无后端/契约/schema 变更**（消费的都是既有冻结端点），纯前端可达性接线。

## Capabilities

### New Capabilities
<!-- 无。 -->

### Modified Capabilities
- `data-platform`: 新增需求——儿童关系图 SHALL 从角色适配的导航可达，且入口以「选择儿童」带入 childId 而非手输原始主键（现有「Graph query API is reachable」是 API 层；本次补前端可达性与非-raw-id 入口）。
- `ai-detection`: 新增需求——平台管理员 SHALL 能通过 UI 管理 AI 模型台账（列表/新增/编辑/停用元数据），接线既有 `AiModelController`。

## Impact

- **frontend/**：`config/menu.ts`（加 GRAPH 入口给 director/teacher、加 AI 모델 관리 给 platform admin）；新增 `services/apis/aiModels.api.ts`；新增 `app/` 下平台管理员模型管理页 + 组件（复用 `useCrudResource`）；`ChildGraphViewer`/graph 页入口改为从名册选择带入 childId（去手输主键）。
- **backend/**：**无改动**（`AiModelController`/`GraphController` 均已存在且契约冻结）。
- **测试**：前端 `npm run lint && npm run build` + 新增 vitest（aiModels api CSRF/参数、模型管理 hook 薄封装、graph 入口带入 childId 的纯逻辑）。
- **不影响**：DB/schema/Flyway、后端 REST 契约、SSE/事件、AI 子栈、通知、多租户后端隔离链（graph 查询的租户隔离已在 Cypher 内强制，前端仍绝不传 kindergartenId）。

## Non-goals

- 不改任何后端代码/契约/schema。
- UX-01 **不做**训练触发、权重上传、把模型绑定到检测服务生效（范围 B，独立立项）。
- 不给 GUARDIAN 开放关系图入口（关系图含跨儿童关系，属敏感，限园所内 director/teacher；后端租户隔离不变）。
- 不新增儿童姓名模糊搜索后端端点（本次仅复用既有 child 列表做选择入口；如需全局姓名搜索另议）。
