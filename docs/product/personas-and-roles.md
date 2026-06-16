# 用户角色与权限范围（Personas & Roles）

## 五类角色

✅ **已确认** 来源：`user_role_enum`（`db/initdb/01_create_schema.sql:24-30`）、`UserRoleEnum.java`、注册逻辑 `AuthService.java`。

| 角色 | 韩文 | 说明 | 关联档案实体 |
| --- | --- | --- | --- |
| `GUARDIAN` | 보호자 | 儿童的保护者/家长 | `guardians` + `child_guardian_relationships` |
| `TEACHER` | 교사 | 任课/带班教师 | `teachers` |
| `KINDERGARTEN_ADMIN` | 원 관리자 | 单个幼儿园的管理员 | `teachers`（✅ 注册时与 TEACHER 走同一档案，见下） |
| `PLATFORM_IT_ADMIN` | 플랫폼 IT 관리자 | 平台 IT 管理员（✅ 2026-06-07 确认：**留给运维人员**） | `superadmins`（🔶 复用，见下） |
| `SUPERADMIN` | 슈퍼관리자 | 超级管理员 = 平台级客户 **교육청（教育厅/行政监管）与 재단（财团）**（✅ 2026-06-07 确认：二者**同属此角色**，非独立画像） | `superadmins` |

## 权限范围（Scope）模型

✅ **已确认**（`user_role_assignments` 表 + `user_role_assignment_scope_type` 枚举 + `AuthService.register()`）：

权限不是直接挂在用户上，而是通过 **`user_role_assignments`（角色分配）** 表达，每条分配带一个**范围（scope）**：

- `scope_type = PLATFORM` → 平台级权限，`scope_id` 为空。用于 `PLATFORM_IT_ADMIN`、`SUPERADMIN`。
- `scope_type = KINDERGARTEN` → 限定在某个幼儿园，`scope_id = kindergarten_id`。用于 `GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN`。

一个用户可拥有多条角色分配（唯一约束 `uq_ura_user_role_scope` 限定 `user_id+role+scope_type+scope_id` 不重复）。当前登录实现要求 user 本身为 `ACTIVE`，并取**最近一条 ACTIVE 分配**作为"当前角色"返回给前端；没有 ACTIVE 分配时返回通用 `401`，不再默认回退 `GUARDIAN`。

## 注册时的角色策略

✅ **已确认**（`AuthService.register()` 的服务端角色分支）。公开注册（`POST /api/v1/auth/register`）只接受四类申请，并把 user、角色分配、业务档案及园级 membership 统一写为 `PENDING`：

| 角色 | 创建的档案 | 备注 |
| --- | --- | --- |
| `GUARDIAN` | `guardians` + `child_guardian_relationships` + `user_kindergarten_memberships` | 专用验证命令只返回匹配布尔值；注册时再次按完整 RRN 定位儿童，scope 与 membership 只从该记录派生 |
| `TEACHER` | `teachers` + `user_kindergarten_memberships` | 需 `kindergartenId` |
| `KINDERGARTEN_ADMIN` | `teachers` + `user_kindergarten_memberships` | 与 TEACHER 复用 `registerTeacher`；只接受 `DIRECTOR` / `VICE_DIRECTOR` level |
| `PLATFORM_IT_ADMIN` | 不创建 | 不开放公开注册；请求在首次 persistence 前以 `400` 拒绝 |
| `SUPERADMIN` | `superadmins` | department 字段记录行政机关+部门 |

> ❓ **待确认**：
> - 登录"当前角色"取最近一条 ACTIVE 分配，对**多角色用户**意味着什么？是否需要前端显式切换 scope？
> - ~~`child_guardian_relationships` 没有 PENDING 状态；当前靠 PENDING guardian/membership/role 阻断授权，审批阶段是否需要独立申请关系模型？~~ ✅ **已决（2026-06-16）**：关系活跃语义采用 **`end_date` 窗**（不加 status 列、无 schema 迁移）；Guardian 审批前 membership/role 为 PENDING → 登录即被拒，批准后既存关系行（`end_date` 空）即活跃。若未来审批需独立关系申请态，再以新 ADR/迁移引入。

## 鉴权现状（必读）

> ✅ **as-built（2026-06-15 PR #89，2026-06-16 续）**：角色/范围模型已**被强制执行**——`/api/v1/**` 默认拒绝 + 服务端会话 + 每请求 `EffectiveAuthorizationContext`（ACTIVE user / 唯一 ACTIVE role / membership / scope）+ 集中 policy + 租户隔离；Teacher 收紧为有效 assignment 覆盖，cameras/streams/sessions 限 `KINDERGARTEN_ADMIN`。JWT 已移除。**安全审计 writer 已落地**（SPEC-0001 #1，登录/登出/会话/tenant 切换/审批/拒绝）。**Guardian→child 关系读取已落地**（SPEC-0001 §349：`GET /children` 仅返回有 ACTIVE 关系——`end_date` 窗——的儿童，无关系 → 隐藏 404 + 审计）。仍 deferred：Teacher→child / 事件资源、Guardian 感谢信/通知。详见 [security-architecture](../architecture/security-architecture.md)。

## 典型场景（🔶 推断，来自前端路由与功能模块）

- **保护者**：目标能力包括查看关联儿童事件、公告、感谢信与通知；当前感谢信公共 API/页面操作已停用，且禁止 live CCTV、录像回放和 detection evidence。
- **教师 / 园管理员**：管理班级/教室/儿童/摄像头，复核检测事件，发布公告。
- **平台 IT（运维人员）**：平台运维、AI 模型与平台级配置；当前前端不加载 live CCTV。
- **超级管理员（교육청 / 재단）**：跨园管理、行政监管视角、跨租户统计；当前前端不加载 live CCTV。
