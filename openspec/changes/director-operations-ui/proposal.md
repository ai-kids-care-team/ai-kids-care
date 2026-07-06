## Why

2026-07-06 分析 **UX-05（MED-HIGH）**：园长（KINDERGARTEN_ADMIN / 원장）**无法在产品内建/改本园运营结构**（班级 classes、教室 rooms、摄像头流 camera_streams）——后端 CRUD 端点齐备且租户域正确，但前端**连 `classes.api.ts`/`rooms.api.ts` 都不存在**，`cctv.api.ts` 只有 GET、无写。园长运营半边职责无法交付，数据须带外播种。经 integration 角度坐实为**纯前端未接线缺口**（非契约错位）。

后端现状（本 change 已核实的冻结契约，见 `api-contract.md`）：
- `ClassController` `/api/v1/classes`：GET(list, `keyword`+分页) / GET/{id} / POST / PUT/{id} / DELETE/{id} —— **全 CRUD**。
- `RoomController` `/api/v1/rooms`：对称 **全 CRUD**。
- `CameraStreamController` `/api/v1/camera_streams`：GET(list) / GET/{id} / POST / PUT/{id} —— **有建/改，无删**。
- `CctvCameraController` `/api/v1/cctv_cameras`：**仅 GET**（设备 CRUD 需后端写端点 = 全栈，**排除**）。

## What Changes

- **新增前端 API 层**（`src/services/apis/`，按现有 `*.api.ts` 模式，双客户端 + CSRF，**绝不传 kindergartenId**）：
  - `classes.api.ts`：list(keyword+分页)/get/create/update/delete。
  - `rooms.api.ts`：对称。
  - 扩展 `cctv.api.ts`（或新增 `cameraStreams.api.ts`，按现有命名择优）：camera_streams 的 create/update（保留既有 GET）。
- **新增运营管理页**（`src/app/**`，园长域）：班级、教室、摄像头流三块管理界面（列表 + 建/改表单 + 删除〔仅 class/room〕+ 空/加载/错误态），Tailwind 与现有页一致；分页用 `PageResponse`。
- **接菜单**（`menu.ts`）：给 KINDERGARTEN_ADMIN 加「운영 관리」入口（班级/教室/摄像头，路由 path 与页面对齐）。
- i18n 韩语文案与现有页风格一致。

## Non-goals

- **不做 cctv_camera 设备 CRUD**（后端仅 GET，需先建写端点 = 全栈，另议）。
- **不做 camera_streams 删除**（后端无 DELETE；如需另加后端端点）。
- 不改任何后端端点/契约（纯前端接线）。
- 不碰 CctvDashboardPage（那是 C5）。
- 不引入教师/家长视角的这些管理面（授权域仍限 KINDERGARTEN_ADMIN）。
