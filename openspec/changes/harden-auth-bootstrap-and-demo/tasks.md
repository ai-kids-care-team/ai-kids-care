## 1. 生产冷启动安全引导(AdminBootstrapRunner)

- [x] 1.1 写集成测试:设 `BOOTSTRAP_ADMIN_LOGIN_ID/PASSWORD` 且库无用户 → 启动后恰好建 1 个 ACTIVE SUPERADMIN(bcrypt、角色分配);已有用户 → 不建;env 未设 → 不建;`login_id='admin'` → 拒绝/不建(先看失败)
- [x] 1.2 实现 `AdminBootstrapRunner implements CommandLineRunner`:读 env、`count()==0` 守卫、建 user+role(+superadmin profile)、不 log 密码、login_id 非 'admin' 校验
- [x] 1.3 `application.yml` 加 `BOOTSTRAP_ADMIN_*` 的 `${ENV}`(无默认);测试转绿

## 2. 登录暴力破解防护(限流 + 锁定)

- [x] 2.1 写 `AuthEndpointTest` 扩展:同一 identifier 连续失败达阈值 → 后续 `429`;锁定中即使正确密码也 `429`;成功登录前清零;锁定/限流写审计(先看失败)
- [x] 2.2 实现 `LoginThrottleService`(`StringRedisTemplate`:窗口失败计数 + TTL 锁 key),阈值/窗口/锁定时长 `${ENV:default}`
- [x] 2.3 `AuthService.login` 接入:入口查锁→命中 `429`;失败 +1;成功清零;事件经 `SecurityAuditWriter`(不记密码);确保 `429` 走显式错误契约(不泄敏感)。测试转绿

## 3. 演示账号文档 + 手动注入事件脚本

- [ ] 3.1 写脚本冒烟测试(testcontainer 或 mock):用 `test-ai-service-token-not-secret-2026` 调 internal ingest 建 session+event 成功;无效 token → 拒绝、不建事件(先看失败)
- [ ] 3.2 实现独立注入脚本(`scripts/` 或 `ai/`):参数化 backend URL/token/streamId/modelId,POST sessions+events,默认指向本地、文档警示仅限演示
- [ ] 3.3 写演示账号文档(各演示账号 login_id/角色/租户/用途 + 端到端演示步骤 + "演示种子永不进生产"声明)

## 4. 前端 demo 提示对齐 + 生产不渲染

- [ ] 4.1 `LoginForm.tsx`:提示文案对齐真实演示 seed 账号(仅 `NEXT_PUBLIC_SHOW_DEMO_HINTS=true` 时显示)
- [ ] 4.2 确认生产 compose/构建 `NEXT_PUBLIC_SHOW_DEMO_HINTS` 不为 true(生产登录页无任何凭据提示)
- [ ] 4.3 前端 node:20 lint+build 绿(注意 React19/Next16 lint 严 + lock 还原)

## 5. 生产重置 runbook + 修正陈旧注释(文档/配置,维护者执行重置)

- [ ] 5.1 修正 `docker-compose.cd.yml` 误称 db:prod "含 initdb 种子" 的注释,与 `Dockerfile.prod`(无 seed)一致
- [ ] 5.2 产出重置 runbook:备份→停栈删卷→确认无 seed 镜像→设引导 env+生产门禁→起栈(Flyway+runner)→登录后改强口令/轮换 env;标破坏性、逐项需维护者批准

## 6. 验证收口

- [ ] 6.1 后端 DooD 全套件 `cleanTest test` 全绿
- [ ] 6.2 前端 node:20 lint+build 绿;脚本冒烟通过
- [ ] 6.3 自检:robot `admin/admin123` 在无 seed 生产 401;引导幂等 + login_id 非 admin;限流 429+清零;无密码进日志;**无 schema 变更(零迁移)**
