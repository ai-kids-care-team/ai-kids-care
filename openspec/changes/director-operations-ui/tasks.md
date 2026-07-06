# Tasks — director-operations-ui (C6 / UX-05)

> 纯 frontend 域，非破坏性。develop 直接提交。对着冻结的 `api-contract.md` 接线。

## 1. API 层（src/services/apis/）
- [ ] 1.1 `classes.api.ts`：list(keyword+分页→PageResponse)/get/create/update/delete，按现有 `*.api.ts` 模式（双客户端 + CSRF，不传 kindergartenId）
- [ ] 1.2 `rooms.api.ts`：对称
- [ ] 1.3 camera_streams 写：扩展 `cctv.api.ts` 或新增 `cameraStreams.api.ts`（create/update，保留既有 GET；密码只提交不回显）
- [ ] 1.4 类型定义与后端 DTO/VO 逐字段对齐（读源码 `entity/dto/*`、`vo/*` 确认字段名/可空/类型）

## 2. 管理页面（src/app/**，KINDERGARTEN_ADMIN 域）
- [ ] 2.1 班级管理页：列表(keyword 搜索+分页) + 建/改表单 + 删除确认 + 空/加载/错误态
- [ ] 2.2 教室管理页：对称
- [ ] 2.3 摄像头流管理页：列表 + 建/改（无删）；密码字段只写不回显
- [ ] 2.4 复用现有组件/表单/分页/Tailwind 约定；错误经既有 `getApiErrorMessage` 展示；404 按「未找到」

## 3. 菜单接线（menu.ts）
- [ ] 3.1 给 KINDERGARTEN_ADMIN 加「운영 관리」入口（班级/教室/摄像头，path 与页面对齐）；其它角色不可见
- [ ] 3.2 i18n 韩语文案与现有页一致

## 4. 门禁
- [ ] 4.1 `cd frontend && npm run test:run`（C1 基建可用；对新 api 的纯函数/映射补少量单测更佳）
- [ ] 4.2 `cd frontend && npm run lint && npm run build` 全绿（build 23+3 路由）
- [ ] 4.3 提交前还原 `next-env.d.ts`
