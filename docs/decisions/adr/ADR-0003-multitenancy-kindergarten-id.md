---
ADR: ADR-0003
title: "ADR-0003: 以 kindergarten_id 实现多租户"
status: Accepted (Retrospective)
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0003: 以 kindergarten_id 实现多租户

> **回溯性 ADR**：描述代码现状，非新提案。

## 状态

Accepted (Retrospective)

## 背景

✅ 系统服务多家幼儿园。几乎所有业务表都带 `kindergarten_id` 列，并将其纳入**复合唯一键**（如 `uq_child_kg_childid (kindergarten_id, child_id)`）与**复合外键**（如 `camera_streams(kindergarten_id, camera_id) → cctv_cameras(kindergarten_id, camera_id)`）。

## 决策

采用**共享数据库、共享 schema、以 `kindergarten_id` 行级区分**的多租户模型。复合键确保跨表引用始终限定在同一租户内，从 schema 层杜绝跨园数据串联。

## 后果

- **正面**：单库单 schema 运维简单；强约束在数据库层防止跨租户引用错误。
- **代价 / 风险**：
  - ❓ **租户隔离的运行时强制依赖应用层**。当前后端鉴权关闭，且未见 service 层统一注入 `kindergarten_id` 过滤——schema 提供了隔离的结构基础，但运行时是否强制隔离待确认（见 [[ADR-0007]] 与 open-questions OQ-SEC-1）。
  - 复合键使 JPA 映射与查询更复杂。

## 考虑过的备选

- ✅ **每租户独立 schema / 独立库——未采用（理由已确认 2026-06-07，维护者）**：
  1. 租户数量可控，避免 N 套库的**运维与学习成本**；
  2. 便于**平台级用户跨租户查询 / 统计**——主要假想平台用户是 **교육청（教育厅）或 재단（财团）**，需要跨园（跨租户）视图，共享库更易实现。

## 关联

- [architecture/data-architecture.md](../../architecture/data-architecture.md#4-多租户模型)
