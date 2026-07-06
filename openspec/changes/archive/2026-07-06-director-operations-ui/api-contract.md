# API 契约 — director-operations-ui (C6)

> 后端**已存在、冻结**；本 change 纯前端接线。实现者须以实际 controller/DTO/VO 为准逐字段核对（下列为已探端点，字段以 `*CreateDTO`/`*UpdateDTO`/`*VO` 源码为准）。所有端点：Cookie 会话 + CSRF 头；租户由 ThreadLocal 派生，**前端绝不传 kindergartenId**。分页 Spring `Page` ↔ 前端 `PageResponse`。

## classes — `/api/v1/classes`（全 CRUD）
| 方法 | 路径 | 请求 | 响应 | 状态 |
|------|------|------|------|------|
| GET | `/api/v1/classes?keyword=&page=&size=` | query: `keyword`(可空)+分页 | `Page<ClassVO>` | 200 |
| GET | `/api/v1/classes/{id}` | — | `ClassVO` | 200 / 404 |
| POST | `/api/v1/classes` | `ClassCreateDTO` | `ClassVO` | 201 |
| PUT | `/api/v1/classes/{id}` | `ClassUpdateDTO`(PATCH 语义, null 忽略) | `ClassVO` | 200 / 404 |
| DELETE | `/api/v1/classes/{id}` | — | — | 204 / 404 |

DTO/VO 字段以源码为准：`entity/dto/ClassCreateDTO.java`、`ClassUpdateDTO.java`、`vo/ClassVO.java`。

## rooms — `/api/v1/rooms`（全 CRUD，与 classes 对称）
GET(list `keyword`+分页) / GET/{id} / POST(`RoomCreateDTO`) / PUT/{id}(`RoomUpdateDTO`) / DELETE/{id}。
字段：`RoomCreateDTO`/`RoomUpdateDTO`/`RoomVO`。

## camera_streams — `/api/v1/camera_streams`（建/改，**无删**）
| 方法 | 路径 | 请求 | 响应 |
|------|------|------|------|
| GET | `/api/v1/camera_streams`（既有 `cctv.api.ts` 已接） | 分页/过滤 | `Page<CameraStreamVO>` |
| GET | `/api/v1/camera_streams/{id}` | — | `CameraStreamVO` |
| POST | `/api/v1/camera_streams` | `CameraStreamCreateRequest` | `CameraStreamVO`(201) |
| PUT | `/api/v1/camera_streams/{id}` | `CameraStreamUpdateRequest`(PATCH 语义) | `CameraStreamVO` |

注意：`streamPassword` 写入后端 AES-256-GCM 加密，**VO 不回明文**；前端表单只提交、不回显密码。字段：`dto/CameraStreamCreateRequest.java`/`CameraStreamUpdateRequest.java`/`vo/CameraStreamVO.java`。

## 前端对齐点
- 授权域 = KINDERGARTEN_ADMIN；页面/菜单仅对该角色可见（后端 `@PreAuthorize(TENANT_SURVEILLANCE_*/…)` 会二次裁决，前端只做入口门控 + 展示后端错误）。
- 跨租户/不可见资源后端一律 404 → 前端按「未找到」展示，不区分 403。
- `keyword` 走后端过滤（勿前端加载后过滤）。

## 排除（Non-goal）
- cctv_cameras 设备写（后端仅 GET）；camera_streams DELETE（后端无）。
