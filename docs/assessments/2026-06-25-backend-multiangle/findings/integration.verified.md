# Integration / 边界角度 findings — backend 对外契约 — VERIFIED

Verifier: finding-verifier (adversarial static, standard tier). 每条 1 票，默认假设假阳性并尝试反驳。
仅复核 severity ∈ {high, medium}（INT-01/02/03 high；INT-04/06/07 medium）；low/info 标 skipped。
方法陷阱排除：frontend 在 repo .gitignore 内，Grep/Glob 工具默认跳过该树会产生「空命中假象」；本轮用裸 ripgrep（不读 gitignore）实搜 frontend/src 坐实「前端缺失」类。

---

```yaml
- id: INT-01
  severity: high
  title: 后端 GET /api/v1/notifications（列表+详情）就绪但前端零消费者——通知收件箱未接线
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败（且修正了工具陷阱）。裸 ripgrep（绕过 gitignore）实搜 frontend/src：services/apis 下确无
      notifications*.api.ts（14 个 api 文件中无），`grep -rI "/notifications" frontend/src` = 0 命中。
      后端 NotificationController GET 端点存在。前端确无消费者，单侧悬空成立。
- id: INT-02
  severity: high
  title: push_subscriptions 全 CRUD 就绪但前端无注册流——Web Push 订阅未接线
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。裸 ripgrep frontend/src 对 push_subscriptions|pushSubscription|serviceWorker|VAPID|
      registerPush|PushManager（-i）= 0 命中 → 前端确无 Service Worker / 订阅注册逻辑。后端 CRUD 已落地，
      家长 PUSH 边界单侧悬空成立。
- id: INT-03
  severity: high
  title: 多组件读端点后端就绪，前端 api 被故意 stub（return null/throw），功能边界悬空
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。实读前端：children.api.ts:12 throw('...unavailable until relationship authorization')；
      guardians.api.ts:14-15 `void userId; return null`（多处 return null）；teachers.api.ts:36/125/134 stub
      （return null / throw）。后端对应 GET 端点存在。确为「后端就绪、前端主动断线」。注：属分阶段交付的已知
      缺口（等授权设计），lead 应判「已知缺口 vs 回归」——但 stub 客观存在，契约单侧悬空事实成立。
```

```yaml
- id: INT-04
  severity: medium
  title: 前端 CctvCameraStatus 联合类型缺 REJECTED——与后端 StatusEnum / DB status_enum 不同步
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。实读 cctv.vo.ts:5 = 'ACTIVE'|'PENDING'|'DISABLED'（3 值）；后端 StatusEnum.java:4-7 含 REJECTED
      （4 值）；DB V2 ADD VALUE REJECTED。前端联合类型确窄于后端枚举，类型契约不同步成立。降级反驳尝试：finding
      自承「CCTV 相机或许不进 REJECTED」——若 camera.status 运行时永不为 REJECTED 则实际不可触发；但该 union 是
      显式命名的状态类型且后端同源枚举确写 REJECTED，类型层面的契约错位客观存在，维持 confirmed（medium 合理，
      非可立即崩溃）。
```

```yaml
- id: INT-05
  severity: low
  verification: { verdict: skipped, note: "low — 未复核" }
```

```yaml
- id: INT-06
  severity: medium
  title: docker-compose.yml 无 ai 推理/告警服务——AI→后端 ingest 链在容器拓扑中无生产者
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败（细节修正）。docker-compose.yml 顶层 services = db/redis/data-loader/backend/frontend；裸搜
      ai:|ai-service|stream_live_alert = 0 命中 → 确无 ai 服务。后端 AI_SERVICE_TOKEN 在 backend env 已配，
      生产者（stream_live_alert_service.py 脚本）未编排，端到端闭环 compose 起不来成立。轻微出入：原文称 6 服务
      含 neo4j，实际本 compose 未列 neo4j，但不影响「无 ai 服务」的核心结论。属设计选择 vs 遗漏由 lead 判，缺接线
      事实确立。
```

```yaml
- id: INT-07
  severity: medium
  title: AI submit_event 的 start_time 与 end_time 同时取 now_iso，告警窗口时长在边界处归零
  verification:
    verdict: confirmed
    method: static
    votes: 1
    note: >
      反驳失败。stream_live_alert_service.py:421 now_iso；:429-430 submit_event(..., now_iso, now_iso, ...)
      确为 start==end。后端 DetectionIngestService:69 原样持久化 req.startTime()/req.endTime()，DB 列 NOT NULL。
      → AI 入库事件时长恒 0。注：seed fixture(42_detection_events_seed.sql) 的 start/end 是不同值（schema 支持
      区分窗口），故这是纯 AI 端 bug 而非 schema 限制，反证 finding 成立。medium 合适（数据/展示质量问题，非崩溃）。
```

```yaml
- id: INT-08
  severity: low
  verification: { verdict: skipped, note: "low — 未复核" }
```

```yaml
- id: INT-09
  severity: info
  verification: { verdict: skipped, note: "info — 未复核" }
```

```yaml
- id: INT-10
  severity: info
  verification: { verdict: skipped, note: "info — 未复核" }
```

---

## 复核备注
- 6 条 high+medium 全 confirmed，0 refuted。
- 工具陷阱告警（供报告「覆盖与局限」）：frontend 树在 repo .gitignore 内，Claude Grep/Glob 默认静默跳过，会把「前端缺失」类 finding 误验为「文件不存在/空命中」。本轮已用裸 ripgrep 绕过坐实；后续任何涉前端的静态复核须同样绕过 gitignore。
- INT-01/02/03 的鉴权深挖（家长读自己通知越权、push 订阅租户归属）原文已建议转 security-analyst——本轮只验「契约单侧悬空」事实，未越界判鉴权。
