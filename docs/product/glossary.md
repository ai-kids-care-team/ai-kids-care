# 领域术语表（Glossary）

本表对照本项目中反复出现的领域术语（韩/英/中），并指明其在代码中的载体。项目源自韩国幼儿园场景，原始注释多为韩文。

✅ 来源：`db/initdb/01_create_schema.sql` 列注释、枚举定义、实体类名。

## 核心实体

| 中文 | English | 한국어 | 代码载体 |
| --- | --- | --- | --- |
| 幼儿园 | Kindergarten | 유치원 | `kindergartens` / `Kindergarten` |
| 班级 | Class | 반/학급 | `classes` / `Class` |
| 教室/空间 | Room | 공간/교실 | `rooms` / `Room` |
| 儿童/原儿 | Child | 원아/아동 | `children` / `Child` |
| 教师 | Teacher | 교사 | `teachers` / `Teacher` |
| 保护者/家长 | Guardian | 보호자 | `guardians` / `Guardian` |
| 用户账户 | User account | 사용자 계정 | `users` / `User` |
| 超级管理员 | Superadmin | 슈퍼관리자/행정청 담당자 | `superadmins` / `Superadmin` |

## 关系与排期

| 中文 | English | 代码载体 |
| --- | --- | --- |
| 儿童-班级分配 | Child–Class assignment | `child_class_assignments` |
| 班级-教师分配 | Class–Teacher assignment | `class_teacher_assignments` |
| 班级-教室分配 | Class–Room assignment | `class_room_assignments` |
| 教室-摄像头分配 | Room–Camera assignment | `room_camera_assignments` |
| 儿童-保护者关系 | Child–Guardian relationship | `child_guardian_relationships` |
| 用户-幼儿园成员关系 | User–Kindergarten membership | `user_kindergarten_memberships` |
| 角色分配 | Role assignment | `user_role_assignments` |

> 🔶 约定：分配/关系表普遍带 `start_date`/`end_date`（或 `start_at`/`end_at`），`end` 为 `NULL` 表示"当前有效"。这是**时间有效区间（temporal validity）**模型，可追溯历史排期。

## CCTV 与 AI

| 中文 | English | 代码载体 / 取值 |
| --- | --- | --- |
| 摄像头 | CCTV camera | `cctv_cameras` |
| 视频流 | Camera stream | `camera_streams`（`stream_type`: MAIN/SUB/SNAPSHOT/RECORDING/OTHER） |
| 流协议 | Protocol | `protocol_enum`: RTSP/ONVIF/HTTP/HTTPS |
| AI 模型 | AI model | `ai_models` |
| 检测会话 | Detection session | `detection_sessions`（一次推理运行，含延迟/FPS 指标） |
| 检测事件 | Detection event | `detection_events` |
| 事件类型 | Event type | `event_type_enum`（ASSAULT/FIGHT/SWOON/WANDER/TRESPASS/KIDNAP…） |
| 事件状态 | Event status | `event_status_enum`（OPEN/ACKNOWLEDGED/IN_REVIEW/RESOLVED/DISMISSED/ESCALATED） |
| 事件复核 | Event review | `event_reviews`（记录状态从 from→result 的流转） |
| 证据文件 | Evidence file | `event_evidence_files`（IMAGE/VIDEO，含保留期与哈希） |
| 严重度 | Severity | `detection_events.severity`（整数等级） |
| 置信度 | Confidence | `detection_events.confidence`（0~1） |
| 持续性规则 | Persistence rule | `stream_live_alert_service.py`：滑动时间窗内命中率达阈值才触发告警（去抖动） |

## 通知与沟通

| 中文 | English | 代码载体 |
| --- | --- | --- |
| 通知 | Notification | `notifications`（PUSH/SMS/EMAIL） |
| 通知规则 | Notification rule | `notification_rules`（目标类型/最小严重度/静默时段） |
| 设备令牌 | Device token | `device_tokens`（IOS/ANDROID push_token） |
| 公告 | Announcement | `announcements` |
| 感谢信 | Appreciation letter | `appreciation_letters`（面向 KINDERGARTEN 或 TEACHER） |
| 去重键 | Dedupe key | `notifications.dedupe_key` |

## 安全与合规

| 中文 | English | 代码载体 / 说明 |
| --- | --- | --- |
| 主民登录号（身份证号） | Resident Registration Number (RRN) | 韩国 주민등록번호。存储拆分为 `rrn_first6`（前6位=出生日期，明文，用于检索）+ `rrn_encrypted`（后位，加密/哈希存储） |
| 多租户隔离键 | Tenant key | `kindergarten_id`（几乎所有业务表都带此列，并进入复合唯一键/外键） |
| 流凭证加密 | Stream credential encryption | `camera_streams.stream_password_ciphertext` + `_iv` + `_key_version`（AES-GCM，见 `AesGcmCryptoUtil`） |
| 审计日志 | Audit log | `audit_logs` |
| 保留期 | Retention | `event_evidence_files.retention_until` |
| 法务保全 | Legal hold | `event_evidence_files.hold` |

## 通用状态枚举

| 枚举 | 取值 |
| --- | --- |
| `status_enum` | ACTIVE / PENDING / DISABLED（贯穿几乎所有实体的通用状态） |
| `gender_enum` | MALE / FEMALE |
| `level_enum`（教师职级） | DIRECTOR(원장) / VICE_DIRECTOR(부원장) / TEACHER / OTHER |
| `relationship_enum`（亲子关系） | FATHER / MOTHER |

> ❓ `relationship_enum` 仅有 FATHER/MOTHER，但列注释提到"부/모/조부모/후견인"（父/母/祖父母/监护人）。枚举与注释不一致——是否需扩展取值？见 [open-questions](../modernization/open-questions.md)。
