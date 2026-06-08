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
✅ 仓库内含 `pg-spring-crud-codegen/`（**原 `scripts/codegen/`，2026-05-29 迁址，见 [ADR-0011](ADR-0011-extract-codegen-subproject.md)**）——一个 Python 工具，内省 PostgreSQL schema（表/列/主键/外键/注释）后用 Mustache 模板生成 6 类 Java 文件（CreateDTO/UpdateDTO/Mapper/VO/Controller/Service）。
✅ 后端 `ddl-auto=validate`：Hibernate 不建表，仅校验实体与既有表匹配。

## 决策

采用经典**分层架构**并明确 DTO/VO 边界（传输层不暴露实体）；数据模型采用**数据库优先（DB-first）**，CRUD 骨架由 `codegen` 从 schema 生成，再手工补充业务逻辑。

## 后果

- **正面**：新增表后可快速产出一致的 CRUD 骨架；分层清晰，符合 `CLAUDE.md` 的"业务逻辑不入传输层"原则。
- **代价 / 风险**：
  - 🔶 codegen 是**一次性脚手架**，与生成后代码无双向绑定；schema 变更需手工同步实体（否则 `validate` 启动失败）。
  - 同构样板代码量大。
- **影响范围**：`backend/`、`pg-spring-crud-codegen/`（原 `scripts/codegen/`）、`db/`。

## 考虑过的备选

- ✅ **运行时由 Hibernate 建表（`ddl-auto=update`）——未采用（理由已确认 2026-06-07，维护者）**：刻意采用 **DB-first**——先 Plan/设计 schema 再实现的工程化工作方式，而非小项目"想到哪写到哪"的 ORM 即兴建表；故 schema 由 DBML→SQL 掌控、Hibernate 仅 `validate` 校验。
- ✅ **手写每个 CRUD——未采用（理由已确认 2026-06-07，维护者）**：出于**效率**与**代码风格统一性**，改用 code generator 先生成 CRUD 骨架（脚手架），再由人工在其上增删——即"codegen 脚手架 + 人工补全"，而非全手写。

## 关联

- [architecture/backend-architecture.md](../../architecture/backend-architecture.md)
- [engineering/backend-guide.md](../../engineering/backend-guide.md)
