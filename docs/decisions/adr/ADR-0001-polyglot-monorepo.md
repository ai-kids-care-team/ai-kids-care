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
- ⚠️ **勘误（2026-06-07，维护者）**：原推断"monorepo 为 1 人 + AI Agent 模式降低上下文切换成本"系**时代错置**——本项目**最初并非 Vibe Coding / AI-Agent 项目**，AI-Agent 维护是 **2026-05-11 之后**才引入的后续模式（见根 README）。monorepo 的**真实动因**见下"考虑过的备选"。

## 考虑过的备选

- ✅ **多仓库（polyrepo）——未采用（理由已确认 2026-06-07，维护者）**：
  1. 团队规模小，**简化整栈 CI/CD 与一键启动**（docker-compose 整栈 + 单 `Jenkinsfile`）；
  2. 项目最终作为**作品集（portfolio）对外展示**，单仓更适合统一呈现；
  3. **仓库所有权 / 长期持有**：团队解散后，各负责人各自持有独立仓库不利于对外展示与长期保有，单仓便于集中持有。

## 关联

- [architecture/system-overview.md](../../architecture/system-overview.md)
