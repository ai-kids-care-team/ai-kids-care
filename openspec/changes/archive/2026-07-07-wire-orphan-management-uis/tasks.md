## 1. 前置定位（实现前必做）

- [ ] 1.1 grep 定位 adminOperations 同侪 api（classes/rooms/cameraStreams）用的 HTTP 客户端与 CSRF 注入方式，作为 `aiModels.api.ts` 的模板
- [ ] 1.2 定位现有承载儿童列表/名册的页面或组件（作为 UX-02 去-raw-id 入口的 childId 来源）；确认 child 列表 API
- [ ] 1.3 读 `StatusEnum` 取值与 `AiModelController.deleteAiModel` 语义，确定 UX-01「停用」= 硬 DELETE 还是软 PUT status（design D4）

## 2. UX-01 — AI 模型台账页（范围 A）

- [ ] 2.1 新增 `services/apis/aiModels.api.ts`：消费 `GET/POST/PUT/DELETE /api/v1/ai_models`（list 支持 keyword+分页），客户端/CSRF 对齐 1.1
- [ ] 2.2 新增平台管理员「AI 모델 관리」页（`app/` 下合适路由）+ 组件，用 `useCrudResource` 薄封装（list/create/update/delete + 文案），字段仅 name/version/status（status label 走 i18n）
- [ ] 2.3 `config/menu.ts` 给 `PLATFORM_IT_ADMIN`（+ `SUPERADMIN` 如适用）加「AI 모델 관리」入口
- [ ] 2.4 表单校验 + 错误/加载/空态 + 停用确认（对齐既有 adminOperations 交互）

## 3. UX-02 — 关系图可达性

- [ ] 3.1 `config/menu.ts` 给 `KINDERGARTEN_ADMIN` + `TEACHER` 加「관계 그래프」入口（GUARDIAN 不加）
- [ ] 3.2 从既有儿童名册/列表提供「查看关系图」跳转，携带 childId 进图页（复用 1.2 的数据源，零新后端）
- [ ] 3.3 `ChildGraphViewer` 入口：接受带入的 childId 直接查询；保留显式选择器作直达（去掉「必须手输原始数字主键」为唯一入口）；前端仍绝不传 kindergartenId
- [ ] 3.4 教师侧沿用既有 teacher-centric graph 查询，验证其入口一致

## 4. 前端测试（vitest）

- [ ] 4.1 `aiModels.api.ts` 单测（CSRF 头注入、list keyword/分页参数拼装、create/update/delete 调用形状）
- [ ] 4.2 模型管理 hook 薄封装冒烟（转发 useCrudResource 正确）
- [ ] 4.3 UX-02 childId 带入的纯逻辑/组件测试（选择→携带 childId→查询）

## 5. 验证门

- [ ] 5.1 前端 `npm run lint && npm run build` 通过（生成静态路由含新页）+ 新 vitest 全绿
- [ ] 5.2 确认零后端改动（`git diff --stat` 不含 `backend/`、无 `db/`、无 migration）
- [ ] 5.3 integration-analyst 复核：`aiModels.api.ts` 逐字段对齐 `AiModelController` 契约（DTO/VO 字段、分页、CSRF）；graph 入口未改后端契约
- [ ] 5.4 门禁清零后 `openspec archive wire-orphan-management-uis -y`（有 data-platform + ai-detection 两 delta）
