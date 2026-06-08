---
ADR: ADR-0002
title: "ADR-0002: PostgreSQL + Neo4j 双存储"
status: Accepted (Retrospective)
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0002: PostgreSQL + Neo4j 双存储

> **回溯性 ADR**：描述代码现状，非新提案。
>
> ⚠️ **勘误（2026-06-07，维护者）**：本 ADR 的"PG 为唯一可信源（SoR）"描述的是 PG **相对 Neo4j** 的角色（PG 权威、Neo4j 派生只读），**不**蕴含"只有后端能写 PG"。曾被误读为"后端是 PG 唯一写入者"——该读法**错误**。AI 子系统将检测数据**直接写入 PG**（见 [ADR-0015](ADR-0015-ai-detection-closed-loop.md) V1）与"PG 为 SoR"**完全一致**（写入的正是权威存储）。"PG=SoR、Neo4j=派生"这一核心判断仍然有效。

## 状态

Accepted (Retrospective)

## 背景

✅ 系统同时使用两种数据库：**PostgreSQL 16**（27 张关系表，强约束）与 **Neo4j 5.19**（关系图）。后端通过 JPA 访问 PG，通过 Neo4j Java Driver（原生 Cypher，`GraphRepository`）访问图库。

✅ 业务核心场景之一是"以儿童为中心"的多跳关系展示（儿童↔班级↔教师↔幼儿园↔保护者），并在前端用 `reagraph` 可视化。

## 决策

以 **PostgreSQL 为唯一可信源（system of record）**，承载全部业务写入与强一致约束；以 **Neo4j 为派生只读视图**，由 `db/ne4j_kindergartens/` 的 Python 加载器从 PG 抽取并构建图，专门服务关系图查询与可视化。

## 后果

- **正面**：多跳关系查询在图库中直观高效，避免关系库多表 JOIN；两库各取所长。
- **代价 / 风险**：
  - ❓ 图为**一次性加载**，PG 变更后不自动同步——存在数据新鲜度问题。
  - 运维复杂度翻倍（两套数据库 + 加载器）。
  - 双写/同步一致性需人工保证。

## 考虑过的备选

- ✅ **仅用 PostgreSQL（递归 CTE 处理层级）——未采用（理由已确认 2026-06-07，维护者）**：引入 Neo4j 是为满足**以儿童为中心的多跳关系查询 + 前端图可视化**（reagraph）需求，图库在该场景更直观高效。

> 注：此前回溯补记曾列"仅用图库"为备选——经维护者确认**从未作为真实选项**（纯图库不适合强约束事务型业务数据），已删除以免误导。

## 关联

- [architecture/data-architecture.md](../../architecture/data-architecture.md)
- [[ADR-0004]]（后端如何访问两库）
