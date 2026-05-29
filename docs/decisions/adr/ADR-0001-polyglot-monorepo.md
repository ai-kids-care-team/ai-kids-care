---
ADR: ADR-0001
title: "ADR-0001: 采用多语言 Monorepo"
status: Accepted (Retrospective)
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0001: 采用多语言 Monorepo

> **回溯性 ADR**：描述代码现状，非新提案。无法从代码证明的理由标注为 🔶/❓。

## 状态

Accepted (Retrospective)

## 背景

✅ 系统由四个技术栈差异很大的组件构成：前端（TypeScript/Next.js）、后端（Java/Spring）、AI（Python/PyTorch）、数据库（SQL/Cypher）。这些组件被放在**同一个 Git 仓库**中（`frontend/`、`backend/`、`ai/`、`db/` 同级）。

✅ `CODEOWNERS` 将各目录划归不同团队（ai/frontend/backend/db），`/docs/` 与根归 leads。

## 决策

采用**单仓库（monorepo）**容纳所有组件，每个组件保留独立的构建工具链（Gradle / npm / pip）与独立 Dockerfile，根 `docker-compose.yml` 负责整栈编排。

## 后果

- **正面**：单次 checkout 获取全栈；跨组件改动（如 schema→后端→前端）可在一个仓库内协调；统一的 CI（单一 `Jenkinsfile`）。
- **代价**：仓库体积大（含 `frontend/node_modules`、AI `outputs/`）；不同语言的工具与 CI 混杂；缺乏组件级独立版本化。
- 🔶 对 1 人维护 + AI Agent 的当前模式，monorepo 反而**降低**上下文切换成本（单一事实来源）。

## 考虑过的备选

- ❓ 多仓库（polyrepo）——理由未记录；推断因团队规模小、组件耦合于同一产品而未采用。

## 关联

- [architecture/system-overview.md](../../architecture/system-overview.md)
