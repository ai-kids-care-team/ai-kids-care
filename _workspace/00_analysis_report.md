# AI Kids Care 多角度组件分析报告
日期：2026-06-25 ｜ 角度：架构 / 安全 / 质量 / 集成 ｜ 团队：component-analysis-team（4 sonnet 分析师 + opus lead）
原始证据：见 `_workspace/{architecture,security,quality,integration}_findings.md`（各角度全文）

## 1. 执行摘要
工程整体结构成熟（清晰分层、严肃的多租户隔离框架、闭环检测、spec 驱动），**当前可运行**；但存在三类需立即处理的问题：
1. **一个未鉴权的儿童 PII 枚举漏洞**（SEC-01）——`/auth/guardian-child-verifications` 无限流，返回 RRN 是否存在的布尔预言机。
2. **多处「能编译、运行时静默失效」的契约/逻辑 bug**——公告改一次就被取消置顶（INT-01）、CCTV 实时流恒为 null（QLT-01）、注册页 loginId 正则吞掉连字符（QLT-02）、SSE 经 Caddy gzip 缓冲被延迟最多 25s（INT-04）。
3. **横向扩展即坏**——SSE 注册表在进程内（ARC-02）、`@Scheduled` 无分布式锁（ARC-03/QLT-05）、外部推送调用在 `@Transactional` 内（ARC-01）。当前单实例无碍，一旦多实例/滚动发布即重复发推或丢事件。

统计（lead 去重 + 统一定级后，共 ~34 条 → 合并为 28 条独立问题）：critical 1 ｜ high 12 ｜ medium 12 ｜ low/info 余下。

## 2. 组件健康度评分
| 组件 | 架构 | 安全 | 质量 | 集成 | 综合 | 一句话 |
|------|------|------|------|------|------|--------|
| backend  | B  | B−  | B   | B−  | **B−** | 分层与隔离框架扎实，扣分在扩展性债 + 事务内外部调用 + 枚举预言机 |
| frontend | —  | B   | C   | C+  | **C+** | 零测试 + 1369 行上帝组件 + 多个真实数据 bug，是最薄弱环节 |
| ai       | B  | B   | B−  | B   | **B**  | 推理服务清晰；ingest dedup 边界 + 上传整块入内存待修 |
| db       | —  | C+  | B−  | —   | **B−** | seed 含真实样貌 RRN/测试 pepper 哈希入库；seed 即 fixture 的脆性 |
| infra    | —  | C+  | —   | —   | **C+** | 生产暴露 Swagger、缺安全响应头、Secure cookie 默认 false、SSE 被 gzip |

## 3. 关键发现（按修复优先级排序）

### P1 — 立即（安全 + 廉价真实 bug，本周）
**1. SEC-01 [critical｜backend｜安全] 未鉴权 RRN 枚举预言机**
`/api/v1/auth/guardian-child-verifications` 为 `permitAll` 且无限流（`LoginThrottleService` 只接了登录路径），返回 `{verified:true/false}`，可对儿童 RRN 做生日攻击枚举在园儿童。
→ 复用现有限流（IP+部分 RRN）或要求注册起始会话令牌。`AuthService.java:138`、`SecurityConfig.java:97`。

**2. INT-01 [high｜cross｜集成] 公告编辑必丢「置顶」状态**
后端序列化 `isPinned`，前端 `getAnnouncementForEdit` 读 `d.pinned`（恒 undefined→false）。每次编辑置顶公告都会静默取消置顶。
→ 改为 `pinned: d.isPinned ?? false`，删除重复的 `pinned` 类型字段。`AnnouncementVO.java` ↔ `announcements.api.ts:171`。

**3. QLT-01 [high｜frontend｜质量] CCTV 实时流地址恒为 null**
`resolveRealCameraTileEmbedUrl` 硬传 `hasApiStream=false`，从不转发真实流 URL（注释里写了正确一行修法但没应用）。实时墙看不到真实摄像头。`CctvDashboardPage.tsx:99`。

**4. QLT-02 [high｜frontend｜质量] 注册页 loginId 正则与登录页分叉**
登录页允许 `-`/`_`（修复 commit 2ac081d），注册页 `useSignupForm.ts:96` 仍 strip 掉 → 用户注册出的账号无法用同样输入登录。
→ 抽公共 `normalizeLoginId` 工具，三处统一。

**5. SEC-02 [high｜infra｜安全] 生产暴露 Swagger / api-docs**
`permitAll` 开放 `/swagger-ui/**`、`/v3/api-docs/**`，且无 `springdoc.*.enabled=false`，泄露完整端点/DTO/内部路径地图。
→ 生产关闭或限 PLATFORM_IT_ADMIN。`SecurityConfig.java:72`。

**6. SEC-06 [high｜backend｜安全] 会话 Cookie `Secure` 默认 false**
仅 prod overlay 设 true；demo/CD/staging 跑基础 compose 即明文下发会话 cookie。
→ 默认改 true，开发显式 opt-out。`application.yml:53`。

### P2 — 近期（扩展性 / 事务 / 实时性）
**7. ARC-02 + QLT-05 [high｜backend｜架构] SSE 注册表在进程内** — 多实例时 A 实例 ingest 的事件到不了连在 B 实例的客户端。→ Redis pub/sub 扇出。`DetectionEventSseService.java:32`。
**8. ARC-03 + QLT-05 [high｜backend｜架构] `@Scheduled` 无分布式锁** — `DeferredNotificationScanner` 多实例重复发推/SMS、状态竞争。→ ShedLock。`DeferredNotificationScanner.java:20`。
**9. ARC-01 [high｜backend｜架构] 外部推送在 `@Transactional` 内** — 持库连接做网络 IO + 推成功但 DB 回滚的投递原子性缺口。→ 事务外发 / outbox。`NotificationService.java:134`。
**10. INT-04 [high(生产)｜infra｜集成] Caddy `encode gzip` 缓冲 SSE** — 单条告警被 gzip 缓冲，最多等 25s 心跳才刷出，「实时」实为延迟。→ SSE 路由禁 gzip。`Caddyfile:19` ↔ `nginx.conf`。
**11. SEC-03 [high｜backend｜安全] 摄像头 `sourceUrl`/`playbackUrl` 无 URL 白名单** — KG_ADMIN 可写内网地址，AI 服务后续抓取 = 两跳 SSRF。→ 协议+主机白名单校验。
**12. QLT-03 [high｜backend｜质量] 9 个 service 的 `keyword` 参数被静默忽略** — 调用方无感的契约破坏。→ 实现 LIKE 过滤或移除参数。

### P3 — 持续改进（质量 / 覆盖 / 一致性）
- **QLT-04** 前端零自动化测试 → 引入 Vitest+RTL，优先 useSignupForm/检测状态映射/RTK 错误路径。
- **SEC-05** seed CSV/SQL 含真实样貌 RRN 与测试 pepper 哈希入库 → 改占位值 + CI 阻断。
- **INT-07** 后端通知收件箱 API 已实现但前端无任何消费者 → 家长/教师看不到通知历史，需接线。
- **QLT-10** 4 个测试硬编码 seed 主键（121L/221L）→ 抽 SeedConstants 按自然键解析。
- **QLT-06** KindergartenAdmin/PlatformAdmin 两审批 service 结构性重复 → 抽公共抽象。
- **ARC-06** `@Async` 用默认 `SimpleAsyncTaskExecutor` 无界线程 → 配有界线程池。
- **ARC-07** Neo4j 全量部署（1G 堆+loader）但 `GraphService` `denyAll()` 死代码 → feature flag 门控或补完。
- **SEC-07/INT-06** evidence `uri`/`dedup_key` 字段校验与 JPA 映射脆性。
- **SEC-08/09/10** 缺安全响应头、CRUD 审计缺口、Bearer 比较长度旁路。
- **QLT-11** 24+ 未用 radix 依赖；**INT-03** AI stream_id 空白字符致 dedup 不一致；**INT-05/08** 死类型字段 / 租户切换后前端状态陈旧。

## 4. 修复路线图
- **立即（本周）**：SEC-01（限流）→ INT-01 / QLT-01 / QLT-02（三个一行级真实 bug）→ SEC-02 / SEC-06（生产配置）。
- **近期（多实例发布前必做）**：ARC-01/02/03 + INT-04（SSE 与调度的扩展性 + 实时性）、SEC-03、QLT-03。
- **持续**：主题化归并——「补齐前端测试」「补齐 tenant 隔离集成测试覆盖（QLT-07）」「seed PII 清理 + 审计覆盖」「通知收件箱接线」。

## 5. 覆盖与局限
- **已覆盖**：backend / frontend / ai / db / infra × 架构 / 安全 / 质量 / 集成（4×5 矩阵基本全覆盖；db 的架构与集成角度较浅）。
- **未动态验证**：本机无 Java/Node/容器，所有结论为**静态阅读**得出（与 env 记忆一致）；标 confidence=medium 的安全项（SEC-03/04/07/10）需运行时/渗透确认。
- **存疑（建议人工确认）**：INT-02（SSE 类型不健全，当前 UI 未可见崩溃但类型不安全）、SEC-04（SUPERADMIN 读 AI 模型元数据是否设计如此）。
- **方法说明**：本报告由「组件多角度分析」harness 自动产出，亦作为该 harness 的首次执行验证（Phase 6-3）。
