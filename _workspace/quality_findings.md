# Quality Findings (quality-analyst, sonnet) — 静态评估，测试未实际执行

- QLT-01 [high｜frontend] `CctvDashboardPage.tsx`（1369 行上帝组件）`resolveRealCameraTileEmbedUrl` 硬传 `hasApiStream=false`，实时流 URL 从不转发 → 恒 null；零测试。`:99`。修：转发 apiStreamUrl + 拆分 + 加测试。
- QLT-02 [high｜frontend] `normalizeLoginId` 三份拷贝，登录页允许 `-_`、注册页 `useSignupForm.ts:96` strip 掉 → 注册账号无法登录。修：抽公共工具。
- QLT-03 [high｜backend] 9 个 service（AiModel/AuditLog/DetectionEvent/EventEvidenceFile/Guardian/NotificationRule/Notification/Superadmin/User）`keyword` 参数被静默忽略调 `findAll`。修：实现 LIKE 或移除参数。
- QLT-04 [high｜frontend] 前端零自动化测试（无 *.test.* / vitest / RTL）。修：引入 Vitest+RTL，优先 useSignupForm/状态映射/RTK 错误路径。
- QLT-05 [medium｜backend] 两个单实例-only 组件（DeferredNotificationScanner / DetectionEventSseService）有注释但无追踪 issue。修：建 issue + 多实例启动告警。（与 ARC-02/03 同根）
- QLT-06 [medium｜backend] `KindergartenAdminApprovalService`(330) 与 `PlatformAdminApprovalService`(270) 结构性重复（含测试 664+611 行）→ 安全修复需手动复制。修：抽 AbstractApprovalService。
- QLT-07 [medium｜backend] `TenantIsolationIntegrationTest` 仅覆盖 rooms/cameras/streams/sessions 4 类；notification_rules/detection_events 列表/children/appreciation_letters 跨租户 404 未验。修：扩展用例。
- QLT-08 [medium｜backend] 密码重置 `AuthService.passwordResets` 是 stub 抛 `IllegalArgumentException`（500/400 不明），且抛前查库 → 存在性时序预言机。修：501 NOT_IMPLEMENTED + 去掉预查询。
- QLT-09 [medium｜ai] `pushover.py` 被 train.py/extract 脚本实际 import，不是「待删 stub」——文档标错有误删风险；`sms.py` 才是真正无生产引用。修：纠正文档/归类 optional extra。
- QLT-10 [high(confidence)｜backend] 4 个测试文件各自硬编码 `GUARDIAN_USER=121L` 等 seed 主键，改 seed 顺序即整片红。修：SeedConstants 按自然键解析。
- QLT-11 [low｜frontend] 24+ 个未用 `@radix-ui/*` 及 reagraph/recharts/cmdk 等依赖（仅 2 个被 import）。修：depcheck 清理。
- QLT-12 [low｜cross] 17 处 `console.warn/error` 作为唯一前端错误信号，无结构化上报。修：logger 包装 + 远端 sink（观测性故事）。

Top-3：CCTV 上帝组件含 live bug 且零测试（QLT-01+04）、9 service keyword 静默失效（QLT-03）、seed 主键耦合 4+ 测试 + 单实例假设（QLT-10+05）。
