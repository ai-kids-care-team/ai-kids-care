## Context

生产被公网 robot 持续扫 `admin/admin123`。调研(develop)厘清现状:

- **生产应为无 seed**:`data-platform` spec「Flyway manages production schema evolution; initdb is for demo/CI only」+「Seed dataset quality」scenario *Production does not depend on seed* 已规定:生产 = `Dockerfile.prod`(`postgres:16-alpine`,**no initdb scripts**)+ Flyway schema-only。`db/Dockerfile`(dev/test)才 `COPY initdb/`(全量,含 `admin/admin123` 与全部演示业务数据)。
- **陈旧注释(已澄清)**:`docker-compose.cd.yml:13` 注释称 db:prod "含 initdb 种子",与 `Dockerfile.prod`「no initdb scripts」冲突。维护者确认生产用 `Dockerfile.prod`(无 seed),现网 admin/admin 系早期全量镜像首灌后持久卷残留 → 需重置;本 change 顺手修正该陈旧注释。
- **无 seed ⇒ 无用户 ⇒ 无人能登录**:真正干净的冷启动生产没有任何账号,必须有引导机制让"持部署 secret 的人"建首个 admin。
- **登录零防护**:`AuthService.login`(identifier 匹配 login_id/email/phone,BCrypt)失败仅抛 401,**无失败计数/锁定/限流**,无密码复杂度。过滤器链有 `EffectiveAuthorizationContextFilter`(每请求过),Redis 已用于 session。
- **menu/common_codes 已被 ADR-0013 治理退役**:`data-platform` spec「dictionary tables (menu and common_codes) SHALL be governed per ADR-0013 and MUST NOT be extended」—— ADR-0013(Accepted 2026-05-29)裁定 `menu`→前端 TS 静态配置、`common_codes`→后端 `GET /api/v1/enums/{name}` 元数据端点+前端 i18n,两表"awaiting independent Implementation",且**MUST NOT be extended、no new Flyway migration SHALL target either table**。两表只在 db/initdb `02/03`、不在 Flyway,故无 seed 生产里它们不存在,`GET /api/v1/menus`、`/common_codes` 会 500;但前端已有静态兜底(`fallbackMenus`、`FALLBACK_GENDER/RELATIONSHIP/TEACHER_LEVEL_OPTIONS`)维持注册/导航。彻底清除归 ADR-0013 独立实现 change,**本 change 不碰两表**(否则违反 spec)。
- **test-anchor 不变量**:`data-platform`「Seed dataset quality」要求 seed 必有 `login_id='admin'`、`user_id=1` 及各角色账号,集成测试依赖。
- **演示流半成品**:静态 seed 数据可走"登录→看板→复核→PUSH";实时"AI→事件→SSE"因 AI 服务未起走不通;无演示账号文档、无手动注入工具。

## Goals / Non-Goals

**Goals:**
- 生产冷启动:无业务 seed + env 引导首个非 'admin' SUPERADMIN(部署者设定强口令)⇒ 持 secret 者能进、robot 扫 `admin/admin123` 扫空。
- 登录暴力破解防护:Redis 失败计数 + 临时锁定 + `429` + 审计。
- 演示启用:演示账号文档 + 手动注入检测事件脚本(无需 AI 服务即可演示实时流);前端 demo 提示生产不渲染、文案对齐真实账号。
- 生产重置 runbook(维护者执行)+ 修正 `docker-compose.cd.yml` 陈旧注释。
- 全程 TDD;后端 DooD 全套件 + 前端 node:20 lint/build 收口。
- **零 schema 变更**。

**Non-Goals:**
- **首登强制改密 / `must_change_password`(本期不做,保持零 schema;引导账号用部署者设定的强口令,轮换由 runbook 手动处理)。**
- **实现 ADR-0013 的 menu/common_codes 退役(menu→前端静态配置、common_codes→`/api/v1/enums` 端点+i18n、删表)—— 另开独立 change。**
- 重命名/删除 seed 的 `admin`(test-anchor 依赖,且演示种子永不进生产)。
- 物理重构 db/initdb 目录(生产无 seed 已由 spec 治理)。
- DB 级持久锁定表(用 Redis TTL);多实例分布式限流强一致(单实例假设,follow-up)。
- 对象存储 evidence;真实 Solapi 发送。

## Decisions

### D1. 生产冷启动引导 = `CommandLineRunner`(env 驱动,幂等)
新增 `AdminBootstrapRunner implements CommandLineRunner`。Spring Boot 保证 Flyway 在 `CommandLineRunner` 之前完成,故 runner 跑时 schema 已就绪。逻辑:读 `BOOTSTRAP_ADMIN_LOGIN_ID`/`BOOTSTRAP_ADMIN_PASSWORD`;当**两者非空且 `userRepository.count()==0`** 时,插入一个 `ACTIVE SUPERADMIN`(bcrypt 密码)+ 角色分配;否则不动。绝不 log 密码;login_id 由部署者定、不得为 `admin`。引导账号的口令即部署者设定的强口令,后续轮换由 runbook 手动处理(本期不做强制改密)。
- **为什么 runner 而非 data.sql / Flyway insert**:凭据是部署期 secret,绝不能写进仓库/迁移;runner 从 env 读、幂等、可静默跳过。
- **替代**:手动 SQL 建账号 —— 否决,易出错、无法纳入"冷启动即安全"的自动保证。

### D2. 登录限流 = Redis 失败计数 + TTL 锁定
新增 `LoginThrottleService`(用既有 `StringRedisTemplate`):key 按 identifier(并叠加 client IP),滑动窗口内计失败数;超阈值写一个带 TTL 的 lock key;`AuthService.login` 入口先查 lock → 命中即 `429`(即使密码正确);失败 +1;成功清零。阈值/窗口/锁定时长用 `${ENV:default}`。锁定/限流写 `SecurityAuditWriter`(不记密码)。
- **为什么 Redis 自建而非 Bucket4j/Resilience4j**:零新依赖、复用 session 的 Redis;需求是基础在线暴破防护,非精密令牌桶。
- **为什么 Redis-only 不落 DB**:`429` 是临时态;持久锁定列是多实例/审计强需求范畴,本期单实例 TTL 足够(`locked_until` 列留作 follow-up)。
- **IP 处理**:生产经 Caddy,真实 IP 在 `X-Forwarded-For` —— 信任边界见 Open Questions;identifier 维度是主防线,IP 为加强项。

### D3. 不改 seed 的 `admin`,安全由"无 seed 生产 + 引导"达成
seed 的 `admin` **保留不动**(`data-platform` test-anchor 要求 `login_id='admin'`/`user_id=1`,测试依赖,且该账号演示种子专用、永不进生产)。"robot 扫不进"由"生产无 seed(无 admin)+ 引导账号 login_id 非 'admin' + 部署者设定强口令"实现。新增 spec「No default or guessable production credentials」把这点固化。

### D4. menu/common_codes 不纳入本 change(遵 ADR-0013)
两表已被 ADR-0013 判定退役(冻结待实现),spec 明令"MUST NOT be extended、no new Flyway migration SHALL target either table"。因此本 change **不为它们建表/加 seed/加迁移**。无 seed 生产里两端点 500 属 ADR-0013 名下既有债,由前端静态兜底(`fallbackMenus`/`FALLBACK_*`)维持注册/导航核心流;彻底清除由独立的 ADR-0013 实现 change 完成。
- **为什么不顺手建表**:违反 data-platform spec;且与 ADR-0013 的最终态(无表)相反,会制造返工。

### D5. 演示启用:复用内部 ingest + 文档 + 前端对齐
手动注入脚本调既有 `POST /api/v1/internal/detection-sessions`+`/detection-events`(Bearer `AI_SERVICE_TOKEN`),参数化 URL/token/streamId/modelId(从 seed `39_camera_streams`/`40_ai_models` 取真实 id),驱动 SSE 看板出事件 —— 与真实 AI ingest 路径完全一致。演示账号文档列各角色/租户/用途。前端 `LoginForm.tsx` 提示文案对齐真实演示账号,生产 `NEXT_PUBLIC_SHOW_DEMO_HINTS` 保持 false(`.env.example` 已注"never in production")。

### D6. 生产重置 runbook(维护者执行,本 change 不执行)
产出文档:① 备份现卷(如需);② 停栈、删 `postgres_data` 卷;③ 确认生产用 `Dockerfile.prod`(无 seed)镜像;④ 设 `BOOTSTRAP_ADMIN_LOGIN_ID/PASSWORD`(强口令)+ `SESSION_COOKIE_SECURE=true` 等既有生产门禁;⑤ 起栈 → Flyway 建 schema → runner 建引导 admin;⑥ 用引导账号登录 → 尽快经现有改密路径或 DBA 改强口令并清/轮换 env。破坏性操作,需维护者逐项批准并在部署环境执行。

## Risks / Trade-offs

- **限流误伤合法用户**(共享 IP/忘密码)→ 阈值/窗口可配 + identifier 维度为主 + 成功即清零;锁定有 TTL 自动解除。
- **menu/common_codes 冷启动 500** → 属 ADR-0013 既有债;前端静态兜底维持注册/导航;彻底解决由 ADR-0013 实现 change 承担,本 change 不引入新风险也不触碰两表。
- **引导口令强度** → 无强制改密,故口令质量靠流程保证:部署者须设强口令(runbook 强调);密码复杂度策略可作 follow-up。
- **注入脚本误用打生产** → 脚本需显式传 URL+token,默认指向本地;文档警示仅限演示环境。

## Migration Plan

- **schema**:无(零 schema)。
- **代码**:bootstrap/限流 均向后兼容(env 未设则 runner 不动;无失败则限流透明)。
- **生产重置**:按 D6 runbook,维护者执行;失败可从备份卷恢复。
- **回滚**:还原后端镜像即可;引导/限流为附加行为,关掉 env/阈值即软退。

## Open Questions

- **改密路径**:引导 admin 想自助改密时走哪条路?当前是否有改密端点?(本期不强制改密,故非阻塞;若缺,可作 follow-up 或由 DBA 轮换。)
- **IP 信任**:Caddy 后取真实 IP 的 `X-Forwarded-For` 信任链如何配置,避免伪造绕过 IP 维度限流。
- **密码复杂度策略**:是否引入注册/改密的最小复杂度校验(当前仅 `@NotBlank`)—— 可纳入本 change 或单列 follow-up。
