## Why

生产被公网 robot 持续扫 `admin/admin123`。根因有二:① 历史上生产库被灌过 db/initdb 演示种子(含 `admin/admin123` 及所有演示账号),`docker-compose.cd.yml` 注释甚至误称 db:prod "含种子" —— 但 `data-platform` spec 与 `Dockerfile.prod` 早已规定生产应为 **Flyway schema-only、不依赖任何 seed**;② 登录无任何暴力破解防护(无失败计数/锁定/限流)。

目标:让生产真正冷启动为无业务 seed 的干净状态,**知道本仓库的人(持部署 secret)能登录,robot 扫不进**。演示侧另需可走通的实时演示流(账号文档 + 手动注入事件),且演示种子明确仅限测试/CI、永不进生产。

本 change **零 schema 变更**:不引入首登强制改密(`must_change_password`),引导账号直接使用部署者设定的强口令,轮换由 runbook 手动处理;menu/common_codes 字典表在无 seed 生产里的缺失归 **ADR-0013(已批准退役、冻结待实现)** 名下,由独立 change 处理,本 change **不碰这两张表**(spec 明令二者不得被新迁移触及)。

## What Changes

- **生产冷启动安全引导(新)**:新增 `CommandLineRunner`,在库内**无任何用户时**,从 env `BOOTSTRAP_ADMIN_LOGIN_ID` + `BOOTSTRAP_ADMIN_PASSWORD` 引导第一个 SUPERADMIN(login_id 由部署者指定、**非 'admin'**;密码 bcrypt;`status=ACTIVE`)。幂等:已有用户则跳过;env 未设则静默不建。robot 扫 `admin/admin123` 在无 seed 的生产上一无所获。
- **登录暴力破解防护(新)**:基于既有 Redis(session 已用)做按 identifier(+IP)失败计数 + 退避/临时锁定;超阈值返回 `429`;成功登录清零;失败/锁定写审计(复用 `SecurityAuditWriter`)。
- **演示账号文档 + 手动注入事件脚本(新)**:文档化演示账号(角色/租户/用途,非 SUPERADMIN 为主)+ 一个独立脚本调 `POST /api/v1/internal/detection-*`(Bearer `AI_SERVICE_TOKEN`)手动灌检测事件,补齐"实时演示流"在 AI 服务未起时的缺口。
- **演示种子边界固化**:确认/守护演示种子(含 test-anchor `admin`/user_id=1)仅供测试/CI/本地,**不进 `db:prod`**;前端 demo 提示(`NEXT_PUBLIC_SHOW_DEMO_HINTS`)在生产为 false,并把提示文案与真实演示账号对齐。
- **修正陈旧注释**:`docker-compose.cd.yml` 误称 db:prod "含 initdb 种子" 的注释,改为与 `Dockerfile.prod`(无 seed)一致。
- **生产重置 runbook**:产出"清空旧生产卷 → 以无 seed 镜像冷启动 → 设引导 env → 登录后改强口令/轮换 env"的操作手册(破坏性、由维护者在部署环境执行;本 change 不执行)。

非目标(Non-goals):**首登强制改密 / `must_change_password` 列(本期不做,保持零 schema)**;**实现 ADR-0013 的 menu/common_codes 退役(menu→前端静态配置、common_codes→`/api/v1/enums` 端点+i18n、删表)——另开独立 change**;重命名/删除 seed 里的 `admin`(test-anchor 不变量依赖,且演示种子永不进生产);物理重构 db/initdb 目录(生产无 seed 已由 spec 治理);DB 级持久锁定表(用 Redis TTL);多实例分布式限流一致性(单实例假设,记 follow-up);对象存储 evidence;真实 Solapi 发送。

## Capabilities

### New Capabilities
- `demo-enablement`: 演示启用 —— 文档化演示账号 + 手动注入检测事件脚本,使端到端演示流(登录→看板→实时事件→复核→通知)可在无 AI 推理服务时走通;生产环境绝不渲染演示凭据提示。

### Modified Capabilities
- `auth-authorization`: 新增生产冷启动安全引导(env 注入首个非 'admin' SUPERADMIN)、登录暴力破解防护(限流+临时锁定+429+审计);并明确"生产不得存在默认/可猜凭据"。

## Impact

- 后端:新增 `AdminBootstrapRunner`、登录限流 service(基于 `StringRedisTemplate`)、`AuthService.login` 接入失败计数/锁定;复用 `SecurityAuditWriter`。**无实体/schema 变更**。
- DB:**无 Flyway 迁移、无 schema 变更**(零 schema)。
- 配置:`application.yml` 加 `BOOTSTRAP_ADMIN_*` 与限流阈值的 `${ENV:default}`;生产 compose 确认 `NEXT_PUBLIC_SHOW_DEMO_HINTS` 不为 true;修正 `docker-compose.cd.yml` 陈旧注释。
- 前端:`LoginForm.tsx` demo 提示文案对齐真实演示账号(仅在 hints 开启时显示)。
- 演示/运维:新增 `scripts/`(或 `ai/`)手动注入脚本 + 演示账号文档 + 生产重置 runbook(文档)。
- spec:`auth-authorization` delta + 新建 `demo-enablement` spec;引用既有 `data-platform`「Production does not depend on seed」「Seed dataset quality(test-anchor)」「dictionary tables (menu and common_codes) … ADR-0013」均不修改。
- 测试:`AuthEndpointTest` 扩展(限流 429、锁定、清零);bootstrap runner 集成测试;注入脚本冒烟。docker DooD 后端全套件 + 前端 node:20 lint/build。
