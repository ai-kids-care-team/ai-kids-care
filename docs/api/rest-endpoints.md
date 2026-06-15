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

✅ 大多数资源型控制器仍保持 codegen 风格的 CRUD 结构（见 [ADR-0004](../decisions/adr/ADR-0004-layered-backend-codegen.md)）：

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| `GET` | `/api/v1/<resource>` | 列表（分页；常带 `keyword` 查询） |
| `GET` | `/api/v1/<resource>/{id}` | 取单条 |
| `POST` | `/api/v1/<resource>` | 创建（201，入参 `CreateDTO`） |
| `PUT` | `/api/v1/<resource>/{id}` | 更新（入参 `UpdateDTO`） |
| `DELETE` | `/api/v1/<resource>/{id}` | 删除（204） |

> 具体每个资源是否实现全部五种方法、是否有额外查询端点，以 Swagger / `/v3/api-docs` 为准。

### Phase 1A 已确认例外（as-built）

以下内容是 **SPEC-0001 Phase 1A 当前已实现状态**，仅表示“敏感数据暴露止血”后的接口形状；**不表示** Session、审批流、tenant enforcement 或角色授权已经完成。

| 资源 | 当前公共方法 | 说明 |
| --- | --- | --- |
| `/api/v1/users` | none | 账户目录含可枚举账户元数据；在 authenticated self/admin contract 落地前不发布公共 operation |
| `/api/v1/children` | none | 儿童资料属于 S1；通用 list/detail、RRN 查询与写删 operation 均关闭，等待关系授权接口 |
| `/api/v1/guardians` | none | Guardian profile 属于 S1；通用读写 operation 均关闭 |
| `/api/v1/teachers` | none | Teacher profile 属于 S1；通用读写 operation 均关闭 |
| `/api/v1/kindergartens` | `GET` list/detail + business-registration lookup | 公共 response 与注册查找只返回最小目录字段；通用 `POST` / `PUT` / `DELETE` 已关闭，不回显 address、business registration number 或联系人信息 |
| `/api/v1/device_tokens` | none | 设备注册元数据与完整 token 均不再公开；等待绑定服务端身份的专用 command/query |
| `/api/v1/event_evidence_files` | none | 证据存在性、保留期、hash 与内部 URI 均不再通过匿名通用 contract 发布 |
| `/api/v1/camera_streams` | `GET` list/detail | 通用写入与删除链已关闭。公共 `CameraStreamVO` 不暴露 `sourceUrl`、`streamUser`、`playbackUrl` 或任何 camera credential/ciphertext/IV/key version 表示 |
| `/api/v1/cctv_cameras` | `GET` list/detail | 通用 `POST` / `PUT` / `DELETE`、Create/Update DTO 与 service/mapper 写链已关闭 |
| `/api/v1/detection_events` | none | 客户端 `kindergartenId` 不能构成授权；list/detail 与通用写链均关闭，等待 authenticated tenant/resource policy |
| `/api/v1/detection_sessions` | `GET` list/detail | 通用 `POST` / `PUT` / `DELETE`、Create/Update DTO 与 service/mapper 写链已关闭 |
| `/api/v1/event_reviews` | none | 公共 operation 与 generic 写链已关闭；等待授权和事件状态同事务的专用复核 command |
| `/api/v1/notification_rules` | none | 公共 operation 与 generic 写链已关闭；等待 tenant-scoped 专用配置接口 |
| `/api/v1/superadmins` | none | 公共 operation 与 generic 写链已关闭；等待平台治理授权接口 |
| `/api/v1/appreciation_letters` | none | 正文与作者/目标关系不再通过匿名通用 CRUD 发布；前端路由显示暂不可用 |
| `/api/v1/graph` | none | 儿童关系图含 S1 关系数据，公共 operation 已关闭；等待资源授权后再开放 |
| `/api/v1/audit_logs` | none | 公共读写 operation 均关闭；未来只允许内部 append writer 与获授权查询 |
| `/api/v1/notifications` | none | 通知内容与投递元数据不再通过通用 CRUD 公开；等待内部发送 command/授权查询 |

## 认证端点（详列）

✅ 来源 `AuthController`（方法级注解已核对）：

| 方法 | 路径 | 说明 | 状态 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 登录，返回 access/refresh + role | ✅ |
| `POST` | `/api/v1/auth/register` | 公开注册申请；`GUARDIAN`/`TEACHER`/`KINDERGARTEN_ADMIN`/`SUPERADMIN` 仅创建 `PENDING` user、role、profile/membership；Guardian scope 从儿童记录派生；管理员 role 仅接受院长/副院长 level；`PLATFORM_IT_ADMIN` 返回 `400` 且不落库 | ✅ Phase 1B |
| `POST` | `/api/v1/auth/guardian-child-verifications` | Guardian 注册前验证儿童完整 RRN；只返回 `{verified}`，不返回 child ID、姓名、班级或其他 PII | ✅ Phase 1B |
| `GET` | `/api/v1/auth/register/availability` | 字段查重（loginId/email/phone） | ✅ |
| `POST` | `/api/v1/auth/refresh` | 刷新令牌 | ✅ |
> `logout`、修改密码、密码重置与验证码流程尚未达到安全开放条件，当前不发布 controller mapping，也不进入 OpenAPI。前端对应入口显示暂不可用。真正实现并连通 service 的只有 `login` / `refresh` / `register` / `register/availability` / `guardian-child-verifications`。
>
> Phase 1B 后，客户端提交的 `status`、`scopeType`、`scopeId` 不属于 `AuthRegisterDTO`，未知字段会被忽略；Guardian 的客户端 `kindergartenId` 不用于 scope 写入。公开申请不能自行激活，审批 API 仍未实现。
>
> 登录/刷新只接受“恰好一条” ACTIVE role assignment；缺失或多条均返回通用 `401`。KINDERGARTEN scope 的登录响应返回服务端派生的 `kindergartenId`。前端还会拒绝缺失/未知 role 或空 access token 的响应。bearer token 当前只保存在 Redux 内存，不写入 localStorage、不自动 refresh；刷新页面后需重新登录。

## 特别说明

- ✅ 检测域数据当前来自种子，非实时 AI（见 [open-questions](../modernization/open-questions.md) OQ-AI-1）；`detection_events` 与 `event_reviews` 当前均不发布公共 operation。
- ✅ `/api/v1/**` 默认 `authenticated`（默认拒绝；PR #89）：会话认证、tenant 隔离、Teacher assignment 策略已落地。仍 deferred：Guardian 关系策略、安全审计。
