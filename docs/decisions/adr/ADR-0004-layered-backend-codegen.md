---
ADR: ADR-0004
title: "ADR-0004: 分层后端 + 代码生成（DB-first）"
status: Accepted (Retrospective)
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0004: 分层后端 + 代码生成（DB-first）

> **回溯性 ADR**：描述代码现状，非新提案。

## 状态

Accepted (Retrospective)

## 背景

✅ 后端各业务模块结构高度同构：`controller → service → repository → entity`，配合 `dto`（入）/`vo`（出）/`mapper`（MapStruct）。
✅ 仓库内含 `scripts/codegen/`——一个 Python 工具，内省 PostgreSQL schema（表/列/主键/外键/注释）后用 Mustache 模板生成 6 类 Java 文件（CreateDTO/UpdateDTO/Mapper/VO/Controller/Service）。
✅ 后端 `ddl-auto=validate`：Hibernate 不建表，仅校验实体与既有表匹配。

## 决策

采用经典**分层架构**并明确 DTO/VO 边界（传输层不暴露实体）；数据模型采用**数据库优先（DB-first）**，CRUD 骨架由 `codegen` 从 schema 生成，再手工补充业务逻辑。

## 后果

- **正面**：新增表后可快速产出一致的 CRUD 骨架；分层清晰，符合 `CLAUDE.md` 的"业务逻辑不入传输层"原则。
- **代价 / 风险**：
  - 🔶 codegen 是**一次性脚手架**，与生成后代码无双向绑定；schema 变更需手工同步实体（否则 `validate` 启动失败）。
  - 同构样板代码量大。
- **影响范围**：`backend/`、`scripts/codegen/`、`db/`。

## 考虑过的备选

- ❓ 运行时由 Hibernate 建表（`ddl-auto=update`）——未采用，选择了 SQL 优先 + validate（更可控、可审计）。
- ❓ 手写每个 CRUD——被 codegen 取代以提效。

## 关联

- [architecture/backend-architecture.md](../../architecture/backend-architecture.md)
- [engineering/backend-guide.md](../../engineering/backend-guide.md)
