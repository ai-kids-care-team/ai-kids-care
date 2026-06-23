## Why

`ai-detection` spec 的「检测闭环目标架构（ADR-0015 V1）」**与维护者的实际决策相悖**，是一处需要更正的 spec 漂移：spec 写「AI 直接写 PostgreSQL、MUST NOT 调后端 REST API 做 ingest、后端用 LISTEN/NOTIFY 才知道有新事件」，但实际决策是 **AI 必须经 Java 后端操作数据库**（后端独占写库）。后续 notifications 余项（规则引擎、检测触发、staff/家长通知）都依赖这个闭环架构 —— 若按漂移的 spec 实现会走错方向。

本 change 是**纯 spec/设计捕获**（不写产品代码）：把 explore 中拍定的正确闭环架构钉进 `ai-detection` 与 `notifications` spec，作为后续实现 change 的依据。

## What Changes

更正 / 澄清以下规范（无代码）：

- **检测摄入走后端 REST 内部端点（更正核心漂移）**：AI 在 `alarm_on` 时 `POST /api/v1/internal/detection-events`（既有 `Authorization: Bearer <AI_SERVICE_TOKEN>` → `ROLE_AI_SERVICE` 通道，ADR-0026），**后端独占写** `detection_sessions/detection_events/event_evidence_files`。删去「AI 直接写 DB」「MUST NOT 调后端 REST」两条错误约束。
- **取消 LISTEN/NOTIFY**：后端即写入方，知道新事件无需 `LISTEN/NOTIFY`；实时推前端看板（SSE/WS）在 ingest 时由后端直接触发。
- **evidence**：视频文件仍由 AI 落盘（`file://`→`s3://`）+ hash，但 `event_evidence_files` **行由后端写**（随 ingest 载荷）。
- **dedup_key 由 AI 端生成**（按 camera + alarm 起始时刻），后端校验唯一，防重连/抖动重复。
- **告警「阈值」= AI 持久化规则，非后端 confidence 标量**：去抖由 AI 端 sliding-window 状态机完成（5s 窗/2s 步、60s 滑窗、单帧 0.60、≥8 命中、命中率 0.50/迟滞 0.40、冷却 120s）；`alarm_on` 即已去抖信号。后端**不**设全局 confidence 阈值。澄清 notifications spec 中「staff 即时告警阈值未定」的 gap。
- **两级通知模型（确认 D-3）**：staff 在 ingest 时即时收「待复核」告警（Pushover + SMS：有哪个用哪个、都有则都用）+ 站内通知 + 前端看板入口；**家长通知严禁绕过复核**，仅在 `event_reviews` 复核确认后由后端经规则引擎发 PUSH。
- **SMS 解耦原则**：后端 SMS 经 provider-agnostic 端口接口（如 `SmsSender`）实现，首个适配器=Solapi；domain 不得耦合具体厂商，换厂商=换适配器。v1 端口先留好、适配器可延后。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `ai-detection`: 更正闭环目标架构 —— AI 经后端 REST 内部端点摄入、后端独占写库、取消 LISTEN/NOTIFY、dedup_key 由 AI 生成、evidence 行由后端写。
- `notifications`: 澄清 staff 即时告警的触发器 = AI persistence-rule alarm（非 confidence 阈值标量）+ staff 通道（Pushover/SMS/站内/看板）；家长通知经复核后由后端发；SMS provider 抽象解耦原则。

## Impact

- **spec 文档**：`ai-detection` + `notifications` delta（随 change，archive 时 sync 进真相源）。
- **无产品代码 / 无 schema / 无迁移**：纯架构决策捕获。
- **解锁后续实现 change（不在本 change）**：① 后端检测摄入端点 + staff 即时告警；② event-review 复核工作流；③ 复核确认→规则引擎→家长 PUSH；④ AI 端 alarm→ingest 客户端（替换 demo 直发）；⑤ SMS 端口 + Solapi 适配器；⑥ 前端实时看板。
