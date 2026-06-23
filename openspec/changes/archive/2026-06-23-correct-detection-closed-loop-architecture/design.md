## Context

捕获一次 explore 中拍定的检测→通知闭环架构，并更正 `ai-detection` spec 的漂移。触发点：spec 写「AI 直接写 PostgreSQL / MUST NOT 调后端 REST / 后端 LISTEN/NOTIFY」与维护者决策「AI 必须经 Java 后端操作 DB」相悖。

既有事实（已核实）：
- AI↔后端正道通道已存在：`/api/v1/internal/**` + `Authorization: Bearer <AI_SERVICE_TOKEN>` → `ROLE_AI_SERVICE`（ADR-0026；`StreamCredentialController` 已用此通道取流凭据）。
- AI 端 `ai/scripts/stream_live_alert_service.py` 的 `update_persistence_state` 是 60s 滑窗迟滞状态机（去抖核心）。
- 后端 PUSH 投递链已就绪（`PushoverService`/`NotificationService.dispatch`/`push_subscriptions` 自助注册 API）。schema 表齐（detection_*/event_reviews/notifications/notification_rules/push_subscriptions）。
- notifications 读 API、push_subscriptions 自助注册已发布。

本 change 不写代码 —— 只钉架构。

## Goals / Non-Goals

**Goals:** 把正确的闭环架构与本次决策写进 ai-detection/notifications spec，消除漂移，作为后续实现 change 的依据。

**Non-Goals:** 不实现摄入端点/复核工作流/规则引擎/AI 客户端/SMS 适配器/前端看板（各自后续 change）；不改 schema。

## Decisions

### D1：检测摄入 = AI → 后端 REST 内部端点（更正漂移）
AI 在 `alarm_on` 时 `POST /api/v1/internal/detection-events`（Bearer AI_SERVICE_TOKEN → ROLE_AI_SERVICE）。**后端独占写** `detection_sessions/detection_events/event_evidence_files`。
- 理由：维护者决策「AI 经 Java 后端操作 DB」；复用既有 ROLE_AI_SERVICE 通道；后端单写入方 = 单一真相、单处校验/审计。
- 否决 spec 原案「AI 直接写 DB + MUST NOT 调 REST」：与决策相悖。

### D2：取消 LISTEN/NOTIFY
后端是写入方，无需 `LISTEN/NOTIFY` 得知新事件；实时推前端看板（SSE/WS）在 ingest 处理时由后端直接触发。
- 理由：LISTEN/NOTIFY 在 spec 原案里是为「AI 直接写 DB、后端被动得知」服务的；REST 摄入后该前提消失，去掉它简化架构。

### D3：dedup_key 由 AI 端生成
AI 按 (camera_id, alarm 起始时刻) 生成 dedup_key，随 ingest 载荷提交；后端对 (kindergarten_id/camera, dedup_key) 校验唯一，重连/抖动不重复建事件。
- 理由：去抖语义在 AI 端（它知道一次 alarm 的边界）；后端唯一约束做兜底。

### D4：告警「阈值」= AI persistence-rule，非后端 confidence 标量（解 D-1 伪命题）
误报抑制由 AI 端 sliding-window 迟滞状态机完成：
```
per 2s → 5s 窗推理 → 黑屏门跳无效窗
 is_hit = target_prob ≥ clip_positive_threshold(0.60)
 60s 滑窗滚动历史；alarm_on 当 span≥30s ∧ hit_count≥~8 ∧ hit_ratio≥0.50
 alarm_off 当 无效窗 / hit_ratio≤0.40（迟滞）；通知冷却 120s
```
`alarm_on` 即去抖信号。后端**不**再设全局 confidence 阈值；要做「哪些 staff/规则关心」的细分用 `notification_rules.min_severity`（已有）。
- 影响：notifications spec「staff 即时告警阈值未定」gap 由此关闭（触发器是 alarm，不是标量阈值）。

### D5：两级通知模型（确认 D-3）
- **staff（即时，复核前）**：ingest 时后端立即通知相关 staff「有待复核事件，请上系统处理」。通道：**Pushover + SMS（有哪个用哪个、都有则都用）** + **站内通知** + **前端实时看板入口**（让 staff 快速进入处理页）。staff 的 Pushover 地址来自其 `push_subscriptions`；SMS 地址用 `users.phone`。
- **家长（复核后）**：**严禁绕过复核**。仅当 staff 写入 `event_reviews` 确认后，后端经规则引擎（`notification_rules`）解析家长收件人 → 建 Notification(PUSH) → dispatch。AI 永不直发家长。

### D6：SMS provider 解耦（D-2）
后端 SMS 经 provider-agnostic 端口接口（如 `SmsSender { send(to, message) }`）；domain/dispatch 只依赖端口。首个适配器 = Solapi（演示用过），但**不得**让 domain 耦合 Solapi SDK/字段。换厂商 = 加/换一个适配器实现。v1：端口接口先留好，Solapi 适配器可延后接。

### D7：evidence 落盘 + 行由后端写
视频证据仍由 AI 落盘（`file://`，可升级 `s3://`）+ hash；`event_evidence_files` 行由后端在 ingest 时写（载荷含 evidence_uri+hash）。视频二进制不入 PG。

## Risks / Trade-offs

- [后端宕机时 AI 摄入失败 → 丢事件] 权衡：REST 摄入使 AI 依赖后端在线（spec 原案的「AI 直接写 DB 不依赖后端」优点丢失）。缓解：AI 端对 ingest 失败做本地缓冲/重试队列 + CSV 仍留底；后端高可用。后续实现 change 需明确重试语义。
- [staff 即时告警仍可能误报扰民] 缓解：alarm 已强去抖；min_severity/规则可再筛；冷却。
- [SMS 抽象过度设计] 缓解：端口接口极薄（send(to,msg)），单适配器，零额外依赖。

## Migration Plan

本 change 仅改 spec 文档（archive 时 sync）。后续实现按序（各自起 change）：
1. 后端检测摄入端点 `POST /api/v1/internal/detection-events` + 写库 + staff 即时告警（Pushover+SMS+站内）。
2. event-review 复核工作流（确认端点）。
3. 复核确认 → 规则引擎 → 家长 PUSH dispatch。
4. AI 端 alarm_on → 调 ingest（替换 demo 直发 pushover/sms）。
5. SMS 端口 + Solapi 适配器。
6. 前端实时看板（SSE/WS）。

## Open Questions

- AI ingest 失败的重试/缓冲语义（本地队列？最多投递几次？）—— 留待实现 change（D 风险）。
- staff 即时告警是否也建 Notification 行（站内可读）还是仅外发 push/SMS + 看板？倾向也落 Notification 行（站内通知一致）。
- 前端看板用 SSE 还是 WebSocket —— 留待前端 change。
