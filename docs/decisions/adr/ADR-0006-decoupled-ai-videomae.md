---
ADR: ADR-0006
title: "ADR-0006: 解耦的 VideoMAE AI 服务"
status: "Accepted (Retrospective); 数据集成部分 Superseded by ADR-0015（2026-06-07 勘误）"
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0006: 解耦的 VideoMAE AI 服务

> **回溯性 ADR**：描述代码现状，非新提案。
>
> ⚠️ **重大勘误（2026-06-07，维护者）**：本 ADR 把"AI 完全不连库 / 与后端解耦"写成了**决策**，并把"AI 直接写库"列为**已否决备选**——这是**错误的**。事实是：当前"不连库"只是**临时演示态**；**原始设计意图一直是 AI 与 DB 集成**（AI 写检测入库、由**后端**从 DB 查询后发送通知，而非推理端发）。AI 中现存的 Pushover/SMS 为**一次性演示代码**（收件人写死、不从 DB 查询）。
> - **仍然有效**：AI 采用独立 Python/PyTorch 技术栈、独立部署（**技术/部署解耦**）；VideoMAE 选型。
> - **被勘误（无效）**：**数据集成层面的"解耦"**——目标为 AI 写库；方向见 [ADR-0015](ADR-0015-ai-detection-closed-loop.md)（V1），其结论**取代**本 ADR 的数据集成相关表述。

## 状态

Accepted (Retrospective) — ⚠️ **数据集成结论于 2026-06-07 勘误、被 [ADR-0015](ADR-0015-ai-detection-closed-loop.md) 取代**（见顶部）；技术栈/部署解耦与 VideoMAE 选型仍有效。

## 背景

✅ 异常行为检测基于 **VideoMAE**（HuggingFace Transformers 视频分类）。AI 代码独立于后端，自带训练（`train.py`）、请求式推理（FastAPI :8001）、实时流告警（`stream_live_alert_service.py`）三条链路。
✅ AI 模块**不连接** PostgreSQL、**不调用**后端 API；实时告警仅通过 Pushover/SMS + 本地 CSV 输出（已全量检索确认）。
✅ AI 服务有独立 `ai/docker-compose.yml`，不在根 compose 中。

## 决策

将 AI 作为**独立部署的子系统**（独立 Python/PyTorch 技术栈、独立 Dockerfile/compose），通过 HTTP（FastAPI）暴露推理能力；选用 VideoMAE 作为视频分类骨干。

> ⚠️ **勘误**：原文此处还隐含"AI 与数据层解耦、不写 DB"——**该部分非决策、已勘误**（见顶部）。**技术/部署解耦**有效；**数据集成解耦无效**，目标为 AI 写库（[ADR-0015](ADR-0015-ai-detection-closed-loop.md) V1）。

## 后果

- **正面**：AI 与业务后端可独立开发、部署、伸缩；Python 生态贴合深度学习。
- **代价 / 风险**：
  - 📋 **检测结果未回流业务系统（当前态）**：实时告警不写 `detection_events`，后端事件数据靠种子。完整闭环（检测→落库→复核→展示→通知）**当前未连通**；**终态方案已定 → [ADR-0015](ADR-0015-ai-detection-closed-loop.md)（V1，Accepted）**——根 README 称实时链路为"实验"。
  - 模型权重不在仓库，需外部提供（`outputs/.../best_model`）。
  - ✅ **已文档化（2026-06-07）**：训练数据集来源与标签体系见 [ai-architecture §1.1](../../architecture/ai-architecture.md) 与 [ADR-0015](ADR-0015-ai-detection-closed-loop.md) 前置（AI Hub 이상행동 CCTV, dataSetSn=171）；`configs/*.yaml` 仍为空（超参待固化回 configs，非阻断）。
- **影响范围**：`ai/`，以及与后端集成的未来工作。

## 考虑过的备选

- ⚠️ ~~AI 直接写库 / 嵌入后端进程——未采用；解耦优先~~ — **此条勘误（2026-06-07）**：AI 直接写库**正是原始设计意图**（非否决备选），见 [ADR-0015](ADR-0015-ai-detection-closed-loop.md) V1。仅"嵌入后端进程"属未采用（AI 保持独立部署）。

## 关联

- [architecture/ai-architecture.md](../../architecture/ai-architecture.md)
- [architecture/integration-and-dataflow.md](../../architecture/integration-and-dataflow.md#8-数据流断点汇总)
