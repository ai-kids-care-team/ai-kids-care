---
ADR: ADR-0015
title: "ADR-0015: AI 检测闭环集成契约（Detection Closed-Loop Integration Contract）"
status: Accepted
date: 2026-06-07
deciders: 接手人起草，维护者 Accept（2026-06-07）；实现委派独立 session
implementation: Not Started
---

# ADR-0015: AI 检测闭环集成契约（Detection Closed-Loop Integration Contract）

> **前瞻提案（方向已定、协议待定）**。维护者已确认（2026-06-07）：AI 闭环是**终态必做项**，只是阶段问题——应在**前置技术债清偿之后**落地。本 ADR **现在起草以消化其最长前置周期**（集成契约、模型与标签澄清），但**实现排在加固轨之后**，且在 OQ-AI 前置澄清解决前不应由 Proposed 转 Accepted。

## 状态（Status）

Accepted（2026-06-07 签署）。**落地次序：加固轨之后**——`0011 ✅ → 0014 → 0012 → 0013 → 0010 → 0009 → **0015**`；**实现委派独立 session**。

> **Implementation note (2026-06-17):** AI-to-DB integration not yet connected; detection_events/detection_sessions tables populated only by seed data as of baseline d0d2269.

> **决议（2026-06-07，维护者 Accept）**：集成媒介 = PostgreSQL 直连；concretization = **V1（AI 直写核心表）**（V2 列 Phase 2 运维后升级）；前置 OQ-AI-2/3 已解决；证据 = 本地→对象可升级；**通知 = 人工复核确认后才通知家长**（园方可选高置信即时预警）。本 ADR 同时**勘误 [ADR-0002](ADR-0002-dual-datastore-postgres-neo4j.md) / [ADR-0006](ADR-0006-decoupled-ai-videomae.md)** 中被误记为决策的"后端唯一写入者 / AI 不连库"。

## 背景（Context）

✅ **事实（核心断裂）**：这是本系统最关键的认知点——**AI 推理与业务后端是两套独立子系统**，闭环"检测 → 落库 → 复核 → 展示 → 通知"物理上未连通（OQ-AI-1）：
- 整个 `ai/` 目录无 DB / 后端调用；唯一的 `requests` 引用在 `ai/src/ai_app/utils/pushover.py`（调外部告警 API）。实时告警仅 Pushover/SMS + CSV（`ai/scripts/stream_live_alert_service.py`）。
- `detection_events`、`detection_sessions`、`event_reviews` 等表当前靠**种子数据**填充，非线上检测产物。

✅ **事实（schema 已就绪，缺的是接线）**：`db/initdb/01_create_schema.sql` 已含闭环所需全部表——`detection_sessions`、`detection_events`、`event_reviews`、`event_evidence_files`（含保留期/法务保全/哈希字段）、`notifications`、`notification_rules`、`device_tokens`。前端 `frontend/src/app/detectionEvents/` 亦已有检测事件页面雏形。**即数据模型与展示层已为闭环预留，仅"AI→持久化"这一段缺失。**

✅ **澄清（2026-06-07，维护者）——纠正一处长期误解**：
- [ADR-0002](ADR-0002-dual-datastore-postgres-neo4j.md) 的"PG 为唯一可信源"指 PG **相对 Neo4j** 的角色（权威 vs 派生），**不**蕴含"只有后端能写 PG"。
- [ADR-0006](ADR-0006-decoupled-ai-videomae.md) 的"AI 不连库 / 解耦"是**临时演示态、非设计决策**。
- **原始设计意图一直是 AI 与 DB 集成**（AI 写检测入库、由**后端**从 DB 查询后发通知）。故 AI 写 PG **不违反**任何真实决策，反而是**回归原设计**；ADR-0002/0006 的相关表述已于 2026-06-07 勘误。

🔶 **推断**：维护者选择"先加固、后造闭环"是合理策略——在不安全（鉴权关闭）、无测试、无迁移设施的底座上接入 AI 写链路，会放大风险。故本 ADR 现在**起草**（消化前置周期），但**实现**待加固轨完成。

## 前置澄清（Prerequisites — 均已于 2026-06-07 澄清/定向）

> 以下各项原为"转 Accepted 的阻断前置"；**2026-06-07 全部澄清/定向，本 ADR 据此转 Accepted**。

1. **[OQ-AI-2] 模型产出与分发** ✅**已解决（2026-06-07）**：基础检查点 `MCG-NJU/videomae-base-finetuned-kinetics`，微调数据 = AI Hub「이상행동 CCTV 영상」(dataSetSn=171, 717h/8,436 clips/12 类)。详见 [ai-architecture.md §1.1](../../architecture/ai-architecture.md)。（剩余非阻断待办：训练超参固化回 `configs`；`best_model` 分发属运维。）
2. **[OQ-AI-3] 模型标签集 ↔ `event_type_enum` 映射** ✅**已解决（2026-06-07）**：12 类与 `event_type_enum` 13 值近 1:1（`OTHER` 为 catch-all），映射表见 [ai-architecture.md §1.1](../../architecture/ai-architecture.md)。**仅剩实现细节**：确认微调模型 `id2label` 实际输出的 label 字符串以写代码级查表。
3. **[OQ-AI-1] 闭环协议本身** ✅待答（本 ADR 即为此提案）：谁写库、用何协议——见"决策"。
4. **证据文件存储** ✅**已定向（2026-06-07）**：**先本地、后对象存储**，做成**可升级抽象**——`event_evidence_files` 存 `evidence_uri + hash`，URI scheme 由 `file://` 平滑升级到 `s3://`/对象存储而不改调用方；读写封装在单一存储接口后。（视频不入 PG。）
5. **AI 的 DB 写入凭据**（V1）：AI **直连 DB 写库、不调后端**，故**不依赖 [ADR-0009](ADR-0009-restore-auth-enforcement.md) 后端鉴权**；只需给 AI 一套**最小权限 DB 写入账号**，走与 JWT secret/pepper 同一密钥范式管理。
6. **会话与去重语义**：谁创建 `detection_sessions`（每路流一会话？）；重连/去抖（脚本已有持续性规则）下如何保证写入**幂等**（`dedup_key`），避免重复事件。
7. **通知 ✅已定（2026-06-07）**：✅**由后端负责**——后端 `LISTEN` 到检测后从 DB 按 `notification_rules` 查 `device_tokens` 发送，**推理端不发通知**（AI 现存 Pushover/SMS 为收件人写死的临时演示代码，落地移除/替换）。✅**时机 = 面向家长的通知必须在人工复核（`event_reviews`）确认后发出**（杜绝误报直推家长造成恐慌）；园方/教职工可选地对高置信检测做即时预警（落地细化）。

## 决策（Decision）

**方向（维护者确认 2026-06-07）**：闭环以 **PostgreSQL 作为集成媒介**——AI 侧**直接写库**，Java 后端**从库读取**并实时推送前端。**不**采用"AI 调后端 REST"的中转（初稿方案 B 已否决，见备选）。排加固轨**最后**实现；现起草消化前置周期。

理由：DB 是**持久、可查询、零新增基础设施**的集成点；且检测**写入的持久性不应依赖后端在线**——安全系统不能因后端重启而丢检测，直写持久存储比经后端中转更稳。

**决策（维护者定 2026-06-07）：采用 V1——AI 直接写业务核心表。**
理由（维护者）：当前系统是"半成品"，原本应打通的全流程从未连上，项目**尚未进入运维周期**；此刻应以**最小新增概念**把原设计的闭环先跑通，而非提前引入更重的架构。V2（落地表 + 后端晋升）是**一段运维之后的整体大升级**，现阶段上不符合项目成熟度——故 V2 列为 **Phase 2 未来演进**（见下）。

### V1（采用）｜AI 直接写业务核心表
AI（Python）直接 `INSERT` 进 `detection_sessions`/`detection_events`/`event_evidence_files`，完成原设计闭环。

> 注：AI 写 PG 是**原始设计意图**，与 ADR-0002（PG 为 SoR）一致；ADR-0002/0006 中"后端唯一写入者 / AI 不连库"是被误记为决策的**临时现状**，已于 2026-06-07 勘误（见背景澄清）——故 AI 写库**非"违反决策"**。下列是 V1 **相对 V2** 的真实代价：

**V1 相对 V2 的代价（已知并接受）**：
- **schema 耦合**：AI 须知核心表结构/约束/复合外键；核心表迁移（[ADR-0012](ADR-0012-production-data-lifecycle.md)）需与 AI 协同。
- **部分业务逻辑进 AI**：label→`event_type_enum` 映射、`kindergarten_id` 租户解析落在 AI 侧（**审计/通知仍由后端**在 `LISTEN` 检测后处理，不进 AI）。

**让 V1 不致成为死胡同的设计约束（现在几乎零成本，省 Phase 2 迁移成本）**：
- AI 侧把"写检测"封装在**单一 detection-sink 模块**后（薄接口）——将来换成写落地表/调 broker 只动这一处。
- enum 映射与租户解析**各自集中一处**（单文件/单函数），不散落——Phase 2 上移到 Java 时是一次受控搬迁。
- 即使 V1 也带**幂等键**（`dedup_key` 或自然唯一约束），避免实时脚本重连/去抖产生重复 `detection_events`。
- 租户上下文**在会话建立时注入**（流配置已知该摄像头属哪个 kindergarten/room），避免 AI 跑实时 join——把"业务逻辑进 AI"压到最小。

### V2（Phase 2 未来演进，暂不做）｜AI 写"落地表"，后端晋升核心表
当系统进入实际运维、并出现以下**触发信号**时，再整体升级到 V2：核心表迁移频繁被 AI 耦合拖累、enum 映射在 Python/Java 间漂移、多路流规模上升需缓冲/背压。V2 把 AI 写入收敛到一张窄落地表（`detection_ingest`：`stream_id, model_id, label, confidence, ts, evidence_uri, evidence_hash, dedup_key, status`），后端监听并**晋升**为核心记录——把核心表写入权收回后端（清晰所有权边界）、把 enum/租户/审计集中回 Java。**因 V1 已按上面的约束隔离了写入点，届时升级成本可控。**

**"后端实时读库"机制（两变体通用，建议）**：用 **PostgreSQL `LISTEN/NOTIFY`**——AI 的 `INSERT`（或落地表触发器）发 `NOTIFY`，后端 `LISTEN` 即时处理并经 SSE/WebSocket 推前端。零新增基础设施、低延迟；轮询为最简降级，逻辑复制/CDC 为重型 overkill。**可靠性兜底**：后端启动/重连时扫描"未晋升/未读"行，弥补 NOTIFY 在断连期间的丢失。

**证据文件（视频）**：不入 PG——AI 写对象存储/文件路径，落地行仅带 `evidence_uri + hash`，对接 `event_evidence_files` 的保留期/法务保全/哈希字段。

## 后果（Consequences）

- **正面**：兑现产品核心价值（检测→复核→通知），把"半成品"全流程**先跑通**；DB 即集成点、**无新增 broker**、最小新增概念；写入持久性不依赖后端在线；schema 与前端页面大体已就绪；**V1 不需要后端鉴权**（AI 直连 DB，比 V2/REST 少一层跨服务依赖，对当前阶段更轻）。
- **负面 / 代价（已知并接受）**：
  - **勘误连带**：AI 写 PG 是原设计意图（与 PG-as-SoR 一致），但需**勘误 [ADR-0002](ADR-0002-dual-datastore-postgres-neo4j.md)/[ADR-0006](ADR-0006-decoupled-ai-videomae.md)** 中"后端唯一写入者 / AI 不连库"的误记表述（落地时更新其状态注）。
  - **schema 耦合** + **业务逻辑进 AI**（enum 映射/租户/审计/通知）——已用"决策"中的隔离约束把未来 V2 迁移成本压到可控。
  - AI **首次获得 DB 写入凭据**（凭据管理；V1 不依赖 ADR-0009 后端鉴权，但仍走统一密钥范式）。
  - 证据视频传输/对象存储方案非平凡；若"落库即通知"有告警疲劳风险（前置澄清 7）。
- **影响范围**：`ai/`（**首次写 DB**，新增 detection-sink 模块 + enum/租户逻辑，打破全解耦）、`backend`（`LISTEN/NOTIFY` 监听核心表 + SSE/WebSocket 推送）、数据模型（可能小幅增列走 [ADR-0012](ADR-0012-production-data-lifecycle.md) 迁移）、通知链路、`frontend/src/app/detectionEvents/`、文档（api / data-architecture / ai-architecture / **更新 ADR-0002 + ADR-0006 状态注**）。

## 考虑过的备选（Alternatives Considered）

- **方案 B — AI 调用后端 ingest REST，由后端写库**（本 ADR 初稿推荐）— 维护者否决：偏好 DB 作集成媒介，且不希望检测写入依赖后端在线（后端宕则写失败）。其"单一写入者"优点已由 **V2（落地表）** 用更解耦的方式达成。
- **V2 落地表+后端晋升** — **未采纳为当前阶段**：接手人原推荐 V2，但维护者判断现阶段（半成品、未进运维周期）应先用 V1 跑通原设计闭环；V2 留作 **Phase 2 运维后的整体升级**（触发信号见"决策"）。本 ADR 的"决策"已为 V1 加入隔离约束，使 Phase 2 升级成本可控。
- **方案 C — 消息队列 / 事件流（Kafka/RabbitMQ/Redis Stream）** — 未采纳为 v1：完全解耦、可缓冲/回放，但为单人维护引入新基础设施。**列为规模化演进**：V2 的落地表本身即"DB 版 inbox"，日后可平滑替换为真正 broker 而不改 AI 契约（Redis 已隐约存在，OQ-OPS-2）。
- **维持现状（仅告警、不落库）** — 否决：核心产品价值将永久不交付。

## 关联（References）

- [open-questions.md](../../modernization/open-questions.md)：OQ-AI-1/2/3（前置）、OQ-DATA-1（PG→Neo4j 是否需要检测数据，v1 倾向不需要）、OQ-OPS-2（Redis）。
- 依赖 [ADR-0012](ADR-0012-production-data-lifecycle.md)（任何 schema 增列走迁移）、[ADR-0014](ADR-0014-test-baseline.md)（落地需测试守护）；联动 [ADR-0013](ADR-0013-dictionary-tables-governance.md)（enum 治理）；**勘误** [ADR-0002](ADR-0002-dual-datastore-postgres-neo4j.md)/[ADR-0006](ADR-0006-decoupled-ai-videomae.md)（"唯一写入者 / AI 不连库"误记）。V1 **不依赖** [ADR-0009](ADR-0009-restore-auth-enforcement.md)（AI 直连 DB、不调后端）。
- 代码：`ai/scripts/stream_live_alert_service.py`、`ai/src/ai_app/serving/app.py`、`db/initdb/01_create_schema.sql`（detection_* / event_* / notifications）、`frontend/src/app/detectionEvents/`。
- [roadmap.md](../../modernization/roadmap.md)。
