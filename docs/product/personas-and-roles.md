# 用户角色与权限范围（Personas & Roles）

## 五类角色

✅ **已确认** 来源：`user_role_enum`（`db/initdb/01_create_schema.sql:24-30`）、`UserRoleEnum.java`、注册逻辑 `AuthService.java`。

| 角色 | 韩文 | 说明 | 关联档案实体 |
| --- | --- | --- | --- |
| `GUARDIAN` | 보호자 | 儿童的保护者/家长 | `guardians` + `child_guardian_relationships` |
| `TEACHER` | 교사 | 任课/带班教师 | `teachers` |
| `KINDERGARTEN_ADMIN` | 원 관리자 | 单个幼儿园的管理员 | `teachers`（✅ 注册时与 TEACHER 走同一档案，见下） |
| `PLATFORM_IT_ADMIN` | 플랫폼 IT 관리자 | 平台 IT 管理员 | `superadmins`（🔶 复用，见下） |
| `SUPERADMIN` | 슈퍼관리자 | 行政监管方/超级管理员 | `superadmins` |

## 权限范围（Scope）模型

✅ **已确认**（`user_role_assignments` 表 + `user_role_assignment_scope_type` 枚举 + `AuthService.register()`）：

权限不是直接挂在用户上，而是通过 **`user_role_assignments`（角色分配）** 表达，每条分配带一个**范围（scope）**：

- `scope_type = PLATFORM` → 平台级权限，`scope_id` 为空。用于 `PLATFORM_IT_ADMIN`、`SUPERADMIN`。
- `scope_type = KINDERGARTEN` → 限定在某个幼儿园，`scope_id = kindergarten_id`。用于 `GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN`。

一个用户可拥有多条角色分配（唯一约束 `uq_ura_user_role_scope` 限定 `user_id+role+scope_type+scope_id` 不重复）。登录时，后端取**最近一条 ACTIVE 分配**作为"当前角色"返回给前端（`AuthService.login()` → `findFirstByUser_IdAndStatusOrderByGrantedAtDesc`，默认回退 `GUARDIAN`）。

## 注册时的角色策略

✅ **已确认**（`AuthService.roleRegisterStrategies`，`AuthService.java:49-55`）。注册（`POST /api/v1/auth/register`）会按角色创建不同的档案：

| 角色 | 创建的档案 | 备注 |
| --- | --- | --- |
| `GUARDIAN` | `guardians` + `child_guardian_relationships` + `user_kindergarten_memberships` | 需提供儿童的 RRN（주민번호）前6+后7位定位儿童，自动绑定为其保护者 |
| `TEACHER` | `teachers` + `user_kindergarten_memberships` | 需 `kindergartenId` |
| `KINDERGARTEN_ADMIN` | `teachers` + `user_kindergarten_memberships` | ✅ 与 TEACHER **复用同一注册函数** `registerTeacher` |
| `PLATFORM_IT_ADMIN` | `superadmins` | ✅ 代码注释 `// TODO 관리자는 없어서 같이 공유함`（没有专门的管理员实体，故与 SUPERADMIN 共用 `superadmins`） |
| `SUPERADMIN` | `superadmins` | department 字段记录行政机关+部门 |

> ❓ **待确认**：
> - `KINDERGARTEN_ADMIN` 与 `TEACHER` 共用 `teachers` 档案、`PLATFORM_IT_ADMIN` 与 `SUPERADMIN` 共用 `superadmins` 档案，是**有意的临时方案**（代码注释暗示如此）还是会演进出独立实体？
> - 登录"当前角色"取最近一条 ACTIVE 分配，对**多角色用户**意味着什么？是否需要前端显式切换 scope？

## 鉴权现状（必读）

> ❓ **重要待确认**：尽管定义了完整的角色/范围模型，但**后端当前未对 API 做任何鉴权**——`SecurityConfig.java` 把 `/api/v1/**` 全部 `permitAll()`，且 JWT 过滤器被注释停用。即角色模型目前**未被强制执行**。前端的 token/refresh 逻辑表明这与设计意图不符。详见 [security-architecture](../architecture/security-architecture.md) 与 [open-questions](../modernization/open-questions.md)。

## 典型场景（🔶 推断，来自前端路由与功能模块）

- **保护者**：查看自己孩子相关的检测事件、公告、给教师写感谢信、接收告警通知。
- **教师 / 园管理员**：管理班级/教室/儿童/摄像头，复核检测事件，发布公告。
- **平台 IT / 超级管理员**：跨园管理、AI 模型与平台级配置、行政监管视角。
