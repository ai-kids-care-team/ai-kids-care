# 功能能力清单（Features）

本清单把产品能力映射到**后端 API 控制器**与**数据表**，便于从功能快速定位实现。

✅ 来源：`backend/.../controller/`（25 个控制器）、`db/initdb/01_create_schema.sql`（28 张表）+ `02_menu.sql`/`03_CommonCode.sql`（`menu`/`common_codes` 字典 2 表）= 共 30 张、前端 `frontend/src/app/` 路由。完整端点见 [api/rest-endpoints.md](../api/rest-endpoints.md)。

## 1. 认证与账户

| 能力 | API 控制器 | 主要数据表 | 状态 |
| --- | --- | --- | --- |
| 公开注册申请 | `AuthController` `/auth/register` | `users`,`user_role_assignments`,`guardians`/`teachers`/`superadmins` | ✅ 允许角色仅创建 PENDING；`PLATFORM_IT_ADMIN` 禁止公开申请；审批未实现 |
| Guardian 儿童身份验证 | `AuthController` `/auth/guardian-child-verifications` | `children` | ✅ 只返回匹配布尔值；通用 `/children/rrn` PII 读取已关闭 |
| 登录 / 刷新 | `AuthController` `/auth/login` `/refresh` | `users`,`user_role_assignments` | ✅ 仅接受恰好一条 ACTIVE role；缺失/多条均 401；登出等待 server-side session |
| 注册字段查重（loginId/email/phone） | `AuthController` | `users` | ✅ |
| 密码重置/验证码 | 当前无公共 mapping | — | ⏸️ 等待限速、一次性 token 与防枚举设计后实现 |

## 2. 幼儿园运营数据

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 幼儿园目录读取/注册查找 | `KindergartenController` | `kindergartens`；仅最小目录字段，通用写入关闭 |
| 班级管理 | `ClassController` | `classes` |
| 教室/空间管理 | `RoomController` | `rooms` |
| 儿童目录 | `ChildrenController` | `children`；当前无公共 operation，等待关系授权 |
| 教师目录 | `TeacherController` | `teachers`；当前无公共 operation，等待 tenant/assignment 授权 |
| 保护者目录 | `GuardianController` | `guardians`；当前无公共 operation，等待 self/admin 授权 |
| 超级管理员管理 | `SuperadminController` | `superadmins`；当前无公共 operation |
| 用户目录 | `UserController` | `users`；当前无公共 operation，等待 self/admin contract |

🔶 **推断**：班级↔教师、班级↔教室、班级↔儿童、儿童↔保护者、教室↔摄像头等多对多关系，由各 `*_assignments` / `*_relationships` 表承载（带 `start_date`/`end_date` 表示时间有效区间，即"历史可追溯"的排期模型）。

## 3. CCTV 与 AI 检测事件

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 摄像头目录 | `CctvCameraController` | `cctv_cameras`；公共契约只读 |
| 视频流脱敏读取 | `CameraStreamController` | `camera_streams`；公共契约只读且不返回播放地址或配置凭据 |
| AI 模型登记 | `AiModelController` | `ai_models` |
| 检测会话 | `DetectionSessionController` | `detection_sessions`；公共契约只读 |
| 检测事件 | `DetectionEventController` | `detection_events`；当前无公共 operation，前端路由显示待授权提示 |
| 事件复核（状态流转） | `EventReviewController` | `event_reviews`；当前无公共 operation |
| 事件证据文件 | `EventEvidenceFileController` | `event_evidence_files`；当前无公共 operation |

✅ 检测事件类型 `event_type_enum`：`ASSAULT`,`FIGHT`,`BURGLARY`,`VANDALISM`,`SWOON`(晕厥),`WANDER`(徘徊),`TRESPASS`(闯入),`DUMP`,`ROBBERY`,`DATEFIGHT`,`KIDNAP`,`DRUNKEN`,`OTHER`。
✅ 事件状态 `event_status_enum`：`OPEN` → `ACKNOWLEDGED` → `IN_REVIEW` → `RESOLVED` / `DISMISSED` / `ESCALATED`。
✅ 证据文件含**保留期**（`retention_until`）、**法务保全**（`hold`）、**完整性哈希**（`hash`）字段——🔶 推断为合规/取证设计。

> ❓ 见 [overview](overview.md#重要边界与现状必读)：这些表当前由种子数据填充，AI 实时链路尚未写库。

## 4. 通知与沟通

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 通知规则（按目标/严重度/静默时段） | `NotificationRuleController` | `notification_rules`；当前无公共 operation |
| 通知记录 | `NotificationController` | 公共 operation 已关闭；表与内部 service 保留 |
| 设备推送令牌 | `DeviceTokenController` | `device_tokens`；当前无公共 operation |
| 公告 | `AnnouncementController` | `announcements` |
| 感谢信 | `AppreciationLetterController` | `appreciation_letters`；当前无公共 operation |

✅ 通知渠道 `notification_channel_enum`：`PUSH`,`SMS`,`EMAIL`。
✅ 通知去重：`notifications.dedupe_key` + 唯一索引 `uq_notifications_dedupe(kindergarten_id, dedupe_key)`。
✅ 后端集成 **Pushover** 客户端（`PushoverService`，依赖 `com.github.sps.pushover.net:pushover-client`）。

## 5. 关系图查询（Neo4j）

| 能力 | 控制器 | 存储 |
| --- | --- | --- |
| 以儿童为中心的关系图 | `GraphController` 当前无公共 operation | Neo4j repository 保留；等待资源授权 |

原内部投影包含某儿童关联的班级、教师、幼儿园和保护者列表；因含 S1 关系数据，公共 API 与前端请求已关闭。详见 [graph-api](../api/graph-api.md)。

## 6. 平台基础

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 公共代码（字典） | `CommonCodeController` | `common_codes`（🔶 由 `03_CommonCode.sql` 建表+初始化） |
| 菜单（按角色） | `MenuController` | `menu`（🔶 由 `02_menu.sql` 建表+初始化；后端**无 `Menu` 实体**） |
| 审计日志 | `AuditLogController` 当前无公共 operation | `audit_logs`；等待内部 append writer 与授权查询 |

`common_codes` 已由 [ADR-0013](../decisions/adr/ADR-0013-dictionary-tables-governance.md) 决定删除并以 backend enum metadata + frontend i18n 取代；该迁移属于独立后续 Implementation，本轮不修改表或现有调用。`audit_logs` 未来只由内部 append writer 写入。

## 前端页面（✅ 来自 `frontend/src/app/`）

- 认证：`(auth)/signup`、`(auth)/forgot-password`、`(auth)/reset-password`
- 公告：`announcements`（read/write/edit）
- 感谢信：`appreciationLetter`、`letters` 路由保留，但当前统一显示暂不可用且不发公共 API 请求
- CCTV：`cctvCamera`、`cctvCameras`
- 检测事件：`detectionEvents` 路由保留，但当前显示暂不可用且不发公共 API 请求

> 🔶 前端页面覆盖的功能子集 **小于** 后端 API 全集（例如班级/教室/保护者的完整 CRUD 页面未在路由中体现），说明前端聚焦于若干核心场景（监控、事件、公告、感谢信、注册登录）。
