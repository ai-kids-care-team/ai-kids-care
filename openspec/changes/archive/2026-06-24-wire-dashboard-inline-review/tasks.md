## 1. 前端 API service

- [x] 1.1 新建 `frontend/src/services/apis/eventReviews.api.ts`：定义 `EventStatusEnum` 字符串联合、`EventReviewCreateDTO`、`EventReviewVO`，及 `confirmEventReview(dto)` → `apiClient.post('/event_reviews', dto)`
- [x] 1.2 （可选）将 `detectionEvents.api.ts` 的 `status` 收窄为 `EventStatusEnum | null`

## 2. 看板内复核动作

- [x] 2.1 `DetectionEventsDashboard` 加 `useSelector` 取 `role`，`canReview = role ∈ {KINDERGARTEN_ADMIN, TEACHER}`
- [x] 2.2 卡片内加动作按钮（`RESOLVED`/`ESCALATED`/`DISMISSED` + 可选 comment 输入），仅 `canReview && 非终态` 时渲染
- [x] 2.3 `onClick` 异步回调：乐观 `setState` 改本卡 status → `confirmEventReview` → 失败回滚原 status + 错误提示（严格遵守 React19 lint：不在 effect body 同步 setState、不在 render 读 ref.current）

## 3. 验证

- [x] 3.1 `docker node:20` lint + build 绿（参考 frontend-verify-via-docker-node；无 `set-state-in-effect`/refs error）
- [x] 3.2 还原 `next-env.d.ts`；`git diff` 确认零后端/schema 改动
