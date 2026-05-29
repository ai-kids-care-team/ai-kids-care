---
ADR: ADR-0006
title: "ADR-0006: 解耦的 VideoMAE AI 服务"
status: Accepted (Retrospective)
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0006: 解耦的 VideoMAE AI 服务

> **回溯性 ADR**：描述代码现状，非新提案。

## 状态

Accepted (Retrospective)

## 背景

✅ 异常行为检测基于 **VideoMAE**（HuggingFace Transformers 视频分类）。AI 代码独立于后端，自带训练（`train.py`）、请求式推理（FastAPI :8001）、实时流告警（`stream_live_alert_service.py`）三条链路。
✅ AI 模块**不连接** PostgreSQL、**不调用**后端 API；实时告警仅通过 Pushover/SMS + 本地 CSV 输出（已全量检索确认）。
✅ AI 服务有独立 `ai/docker-compose.yml`，不在根 compose 中。

## 决策

将 AI 作为**独立子系统**，通过 HTTP（FastAPI）暴露推理能力，技术栈（Python/PyTorch）与后端（Java）解耦；选用 VideoMAE 作为视频分类骨干。

## 后果

- **正面**：AI 与业务后端可独立开发、部署、伸缩；Python 生态贴合深度学习。
- **代价 / 风险**：
  - ❓ **检测结果未回流业务系统**：实时告警不写 `detection_events`，后端事件数据靠种子。完整闭环（检测→落库→复核→展示→通知）**未连通**——根 README 称实时链路为"实验"。
  - 模型权重不在仓库，需外部提供（`outputs/.../best_model`）。
  - ❓ 训练数据集来源与标签体系未在仓库内文档化；`configs/*.yaml` 为空。
- **影响范围**：`ai/`，以及与后端集成的未来工作。

## 考虑过的备选

- ❓ AI 直接写库 / 嵌入后端进程——未采用；解耦优先，集成留待后续。

## 关联

- [architecture/ai-architecture.md](../../architecture/ai-architecture.md)
- [architecture/integration-and-dataflow.md](../../architecture/integration-and-dataflow.md#8-数据流断点汇总)
