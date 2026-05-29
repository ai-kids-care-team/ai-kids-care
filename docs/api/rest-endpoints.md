# 后端 REST 端点目录

✅ 来源：`backend/.../controller/`（25 个控制器，类级 `@RequestMapping` 已逐一核对）。**字段级 schema 以 Swagger 为准**（见 [api/README.md](README.md)）。

## 资源总表（25 个控制器）

| 域 | 基址 `/api/v1/...` | 控制器 |
| --- | --- | --- |
| 认证 | `/auth` | `AuthController` |
| 用户 | `/users` | `UserController` |
| 幼儿园 | `/kindergartens` | `KindergartenController` |
| 班级 | `/classes` | `ClassController` |
| 教室 | `/rooms` | `RoomController` |
| 儿童 | `/children` | `ChildrenController` |
| 教师 | `/teachers` | `TeacherController` |
| 保护者 | `/guardians` | `GuardianController` |
| 超级管理员 | `/superadmins` | `SuperadminController` |
| CCTV 摄像头 | `/cctv_cameras` | `CctvCameraController` |
| 视频流 | `/camera_streams` | `CameraStreamController` |
| AI 模型 | `/ai_models` | `AiModelController` |
| 检测会话 | `/detection_sessions` | `DetectionSessionController` |
| 检测事件 | `/detection_events` | `DetectionEventController` |
| 事件复核 | `/event_reviews` | `EventReviewController` |
| 证据文件 | `/event_evidence_files` | `EventEvidenceFileController` |
| 通知 | `/notifications` | `NotificationController` |
| 通知规则 | `/notification_rules` | `NotificationRuleController` |
| 设备令牌 | `/device_tokens` | `DeviceTokenController` |
| 公告 | `/announcements` | `AnnouncementController` |
| 感谢信 | `/appreciation_letters` | `AppreciationLetterController` |
| 关系图 | `/graph` | `GraphController`（见 [graph-api](graph-api.md)） |
| 公共代码 | `/common_codes` | `CommonCodeController` |
| 菜单 | `/menus` | `MenuController` |
| 审计日志 | `/audit_logs` | `AuditLogController` |

## 标准 CRUD 约定

✅ 以 `ChildrenController` 为确证样例的统一模式（🔶 推断大多数资源型控制器同构，因均由 codegen 生成，见 [ADR-0004](../decisions/adr/ADR-0004-layered-backend-codegen.md)）：

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| `GET` | `/api/v1/<resource>` | 列表（分页；常带 `keyword` 查询） |
| `GET` | `/api/v1/<resource>/{id}` | 取单条 |
| `POST` | `/api/v1/<resource>` | 创建（201，入参 `CreateDTO`） |
| `PUT` | `/api/v1/<resource>/{id}` | 更新（入参 `UpdateDTO`） |
| `DELETE` | `/api/v1/<resource>/{id}` | 删除（204） |

> 具体每个资源是否实现全部五种方法、是否有额外查询端点（如 children 的 `GET /children/rrn`），以 Swagger 为准。

## 认证端点（详列）

✅ 来源 `AuthController`（方法级注解已核对）：

| 方法 | 路径 | 说明 | 状态 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 登录，返回 access/refresh + role | ✅ |
| `POST` | `/api/v1/auth/logout` | 登出 | ✅（🔶 无状态下的语义待确认） |
| `POST` | `/api/v1/auth/register` | 注册（按角色建档） | ✅ |
| `GET` | `/api/v1/auth/register/availability` | 字段查重（loginId/email/phone） | ✅ |
| `POST` | `/api/v1/auth/refresh` | 刷新令牌 | ✅ |
| `PATCH` | `/api/v1/auth/password` | 修改密码 | 🔶 |
| `POST` | `/api/v1/auth/password-resets` | 申请密码重置 | ❓ **未实现**（抛 `Not implemented`，OQ-PROD-3） |
| `PATCH` | `/api/v1/auth/password-resets/{resetToken}` | 用令牌重置密码 | 🔶 |
| `POST` | `/api/v1/auth/verification-codes` | 发送验证码 | 🔶 |
| `POST` | `/api/v1/auth/verification-codes/{challengeId}/verifications` | 校验验证码 | 🔶 |

> 标 🔶 者：端点存在，但具体实现完成度需核对对应 service（部分认证流程可能为占位）。

## 特别说明

- ✅ 检测域（`/detection_events`、`/detection_sessions`、`/event_reviews`、`/event_evidence_files`）数据当前来自种子，非实时 AI（见 [open-questions](../modernization/open-questions.md) OQ-AI-1）。
- ✅ `/camera_streams`、`/detection_events` 在前端注释中被记为"对无认证调用返回 401"——与当前后端 permitAll 存在张力（OQ-SEC-1）。
