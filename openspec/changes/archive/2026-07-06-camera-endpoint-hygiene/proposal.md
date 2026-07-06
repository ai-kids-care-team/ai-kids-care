## Why

C6（director-operations-ui）门禁 integration 复核暴露的两条**非阻断遗留债**（当时判为 follow-up）：

- **C6-gap-a**：`GET /api/v1/camera_streams`（`CameraStreamController.java:28`）与 `GET /api/v1/cctv_cameras`（`CctvCameraController.java:24`）把 `kindergartenId` 声明为**必填 `@RequestParam`**，前端只能沿用既有 `resolveViewerSessionKindergartenId(user)` 从 session 塞值——**字面违反「前端绝不传 kindergartenId」invariant**。service 层已用 ThreadLocal `activeKindergartenId` 二次校验（传错→404，非越权），但契约层面该 param 是多余且误导的。
- **C6-gap-b**：`CameraStreamTypeEnum`/`ProtocolEnum` **未注册进** `EnumMetadataService.REGISTRY`（`EnumMetadataService.java:30-37` 仅 6 项），故 `GET /api/v1/enums/{name}` 查不到；前端 `CameraStreamsSection.tsx:15-16` **硬编码**这两个下拉的枚举值——违反「enum 值取 `/enums`」invariant（当前三侧值一致无运行时错位，但失去漂移防护）。

## What Changes

- **C6-gap-a（backend + frontend）**：把两个 GET 端点的 `kindergartenId` 改为 `required = false`（保留参数以兼容既有前端调用，但不再必填），service 一律以 ThreadLocal `activeKindergartenId` 为准（现状已如此）；**前端**去掉这两个 GET 的 `resolveViewerSessionKindergartenId` 传参 workaround，不再发 `kindergartenId`。行为不变（仍是本租户数据），只清掉误导契约。
- **C6-gap-b（backend + frontend）**：`EnumMetadataService.REGISTRY` 增 `"camera_stream_type" → CameraStreamTypeEnum.class`、`"protocol" → ProtocolEnum.class`（`Map.of` 8 项，仍在 10 上限内）；**前端** `CameraStreamsSection` 改走 `/api/v1/enums/camera_stream_type`、`/api/v1/enums/protocol`（复用 `useStatusOptions` 同款 fetch+FALLBACK 兜底模式），移除硬编码 `STREAM_TYPE_OPTIONS`/`PROTOCOL_OPTIONS`（可保留为 fetch 失败时的 fallback）。

## Non-goals

- 不改这两个 GET 的**响应结构**、不动写端点（POST/PUT 本就不收 kindergartenId）。
- 不改 enum 值本身（camera_stream_type: MAIN/SUB/SNAPSHOT/RECORDING/OTHER；protocol: RTSP/ONVIF/HTTP/HTTPS，三侧已一致）。
- 不动其它 controller 的 kindergartenId 用法（本 change 只收口这两个 camera GET）。
