# 功能能力清单（Features）

本清单把产品能力映射到**后端 API 控制器**与**数据表**，便于从功能快速定位实现。

✅ 来源：`backend/.../controller/`（25 个控制器）、`db/initdb/01_create_schema.sql`（28 张表）+ `02_menu.sql`/`03_CommonCode.sql`（`menu`/`common_codes` 字典 2 表）= 共 30 张、前端 `frontend/src/app/` 路由。完整端点见 [api/rest-endpoints.md](../api/rest-endpoints.md)。

## 1. 认证与账户

| 能力 | API 控制器 | 主要数据表 | 状态 |
| --- | --- | --- | --- |
| 注册（分角色建档） | `AuthController` `/auth/register` | `users`,`user_role_assignments`,`guardians`/`teachers`/`superadmins` | ✅ |
| 登录 / 刷新 / 登出 | `AuthController` `/auth/login` `/refresh` `/logout` | `users`,`user_role_assignments` | ✅ 登录/刷新；**登出=待开发占位**（`throw "Not implemented"`） |
| 注册字段查重（loginId/email/phone） | `AuthController` | `users` | ✅ |
| 密码重置 | `AuthController` `/auth/password-resets` | — | ❓ **未实现**（抛 `Not implemented`） |

## 2. 幼儿园运营数据

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 幼儿园管理 | `KindergartenController` | `kindergartens` |
| 班级管理 | `ClassController` | `classes` |
| 教室/空间管理 | `RoomController` | `rooms` |
| 儿童管理（含按 RRN 查询） | `ChildrenController` | `children` |
| 教师管理 | `TeacherController` | `teachers` |
| 保护者管理 | `GuardianController` | `guardians` |
| 超级管理员管理 | `SuperadminController` | `superadmins` |
| 用户管理 | `UserController` | `users` |

🔶 **推断**：班级↔教师、班级↔教室、班级↔儿童、儿童↔保护者、教室↔摄像头等多对多关系，由各 `*_assignments` / `*_relationships` 表承载（带 `start_date`/`end_date` 表示时间有效区间，即"历史可追溯"的排期模型）。

## 3. CCTV 与 AI 检测事件

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 摄像头管理 | `CctvCameraController` | `cctv_cameras` |
| 视频流管理（含加密凭证） | `CameraStreamController` | `camera_streams` |
| AI 模型登记 | `AiModelController` | `ai_models` |
| 检测会话 | `DetectionSessionController` | `detection_sessions` |
| 检测事件 | `DetectionEventController` | `detection_events` |
| 事件复核（状态流转） | `EventReviewController` | `event_reviews` |
| 事件证据文件 | `EventEvidenceFileController` | `event_evidence_files` |

✅ 检测事件类型 `event_type_enum`：`ASSAULT`,`FIGHT`,`BURGLARY`,`VANDALISM`,`SWOON`(晕厥),`WANDER`(徘徊),`TRESPASS`(闯入),`DUMP`,`ROBBERY`,`DATEFIGHT`,`KIDNAP`,`DRUNKEN`,`OTHER`。
✅ 事件状态 `event_status_enum`：`OPEN` → `ACKNOWLEDGED` → `IN_REVIEW` → `RESOLVED` / `DISMISSED` / `ESCALATED`。
✅ 证据文件含**保留期**（`retention_until`）、**法务保全**（`hold`）、**完整性哈希**（`hash`）字段——🔶 推断为合规/取证设计。

> ❓ 见 [overview](overview.md#重要边界与现状必读)：这些表当前由种子数据填充，AI 实时链路尚未写库。

## 4. 通知与沟通

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 通知规则（按目标/严重度/静默时段） | `NotificationRuleController` | `notification_rules` |
| 通知记录 | `NotificationController` | `notifications` |
| 设备推送令牌 | `DeviceTokenController` | `device_tokens` |
| 公告 | `AnnouncementController` | `announcements` |
| 感谢信 | `AppreciationLetterController` | `appreciation_letters` |

✅ 通知渠道 `notification_channel_enum`：`PUSH`,`SMS`,`EMAIL`。
✅ 通知去重：`notifications.dedupe_key` + 唯一索引 `uq_notifications_dedupe(kindergarten_id, dedupe_key)`。
✅ 后端集成 **Pushover** 客户端（`PushoverService`，依赖 `com.github.sps.pushover.net:pushover-client`）。

## 5. 关系图查询（Neo4j）

| 能力 | 控制器 | 存储 |
| --- | --- | --- |
| 以儿童为中心的关系图 | `GraphController` `/graph/children/{childId}` | Neo4j（`GraphRepository` 原生 Cypher） |

✅ 返回某儿童关联的 班级 → 教师 → 幼儿园，以及其保护者列表（按 priority 排序）。前端用 `reagraph` 可视化。详见 [graph-api](../api/graph-api.md)。

## 6. 平台基础

| 能力 | 控制器 | 数据表 |
| --- | --- | --- |
| 公共代码（字典） | `CommonCodeController` | `common_codes`（🔶 由 `03_CommonCode.sql` 建表+初始化） |
| 菜单（按角色） | `MenuController` | `menu`（🔶 由 `02_menu.sql` 建表+初始化；后端**无 `Menu` 实体**） |
| 审计日志 | `AuditLogController` | `audit_logs` |

🔶 **推断**：`menu` + `common_codes` 支撑前端的**角色化菜单**与下拉字典。`audit_logs` 记录操作审计（action/resource/ip/user_agent），但是否在各写操作中被实际写入需核对各 Service（❓）。

> ❓ **需复审（非原作者设计）**：`menu`、`common_codes` 不属于原作者的领域建模，命名/结构与核心表不一致（`menu` 单数、`common_codes` 复数；`menu` 无后端实体），是否改名/重设计待评估，见 [open-questions](../modernization/open-questions.md) OQ-DATA-4。

## 前端页面（✅ 来自 `frontend/src/app/`）

- 认证：`(auth)/signup`、`(auth)/forgot-password`、`(auth)/reset-password`
- 公告：`announcements`（read/write/edit）
- 感谢信：`appreciationLetter`、`letters`（read/write/edit）
- CCTV：`cctvCamera`、`cctvCameras`
- 检测事件：`detectionEvents`（read）

> 🔶 前端页面覆盖的功能子集 **小于** 后端 API 全集（例如班级/教室/保护者的完整 CRUD 页面未在路由中体现），说明前端聚焦于若干核心场景（监控、事件、公告、感谢信、注册登录）。
