---
ADR: ADR-0011
title: "ADR-0011: 将 pg-spring-crud-codegen 抽离为独立工程"
status: Accepted
date: 2026-05-29
deciders: 维护者（2026-05-29 Accept；推荐方案 A 仓内迁址 + 软指针）
---

# ADR-0011: 将 pg-spring-crud-codegen 抽离为独立工程

> **前瞻提案**。维护者于 2026-05-29 表达意向（OQ-ARCH-3）：将"数据库逆向工程自动生成 Java 代码"的能力分离为独立子工程，便于后期从本仓库拆出。属 module-boundary 变更，故立 ADR。

## 状态（Status）

Accepted（2026-05-29 签署；执行方案 A：仓内迁址 + 软指针）。**已实施于 2026-05-29**——见下方"实施记录"。

## 实施记录（Implementation Log）

**2026-05-29 完成**：
- 14 个文件整体从 `scripts/codegen/` 移至 `pg-spring-crud-codegen/`（5 .py + 6 .mustache 模板 + `.env.example` + `docker-compose.yml` + `requirements.txt`）。
- `pg-spring-crud-codegen/docker-compose.yml:12` 相对路径由 `../../db/initdb/01_create_schema.sql` 修正为 `../db/initdb/01_create_schema.sql`（脱嵌套一层）。
- 新增 `pg-spring-crud-codegen/README.md`（自洽子工程说明：定位 / 依赖 / 配置 / 运行 / 与主仓 docker-compose 联动 / 未来拆仓提示）+ `scripts/codegen/README.md`（软指针）。
- `CODEOWNERS` 增补 `/pg-spring-crud-codegen/` 条目（归 leads）。
- 同步 13 处内部引用：[ADR-0004](ADR-0004-layered-backend-codegen.md)、根 `README.md`/`.en.md`/`.zh-CN.md`、`docs/README.md`、`docs/architecture/{system-overview,backend-architecture,data-architecture}.md`、`docs/engineering/backend-guide.md`、`docs/modernization/open-questions.md` OQ-ARCH-3。

**未做（按 ADR 范围）**：未拆出独立 git 仓库——保持仓内迁址形态；日后用 `git filter-repo`（或 `git subtree split`）带历史拆出留待后续 ADR / 专项任务。

**验证**（已执行）：grep 全库 `scripts/codegen` 无遗留活跃引用——剩余出现皆为历史/软指针/ADR 内部上下文，已加迁址说明。

## 背景（Context）

> 本节为 ADR 起草时（2026-05-29 落地前）的现状描述。落地后视角见上方"实施记录"。

✅ 当前实际生效的代码生成器位于 `scripts/codegen/`（Python：`introspect_pg.py` 内省 → `pystache` 渲染 `templates/*.mustache` → 6 类 Java 文件），见 [ADR-0004](ADR-0004-layered-backend-codegen.md)。
✅ 根目录另有 `pg-spring-crud-codegen/`——经核对为**空目录**（无任何文件）。
✅ **已澄清（2026-05-29，维护者）**：`pg-spring-crud-codegen` 是**预留给 `scripts/codegen` 同一工具的迁址位置**——它**不是 Java 重写、不是平行实现**，目的就是把现有 Python 工具搬过去（保持工具语言不变），并使其在未来可作为独立子工程从本仓库拆出。

> 即"不存在两套生成器并存的风险"。决策因此从"在多套方案中择一"简化为"何时、以何种方式执行迁址"。

## 决策（Decision）

将现有 `scripts/codegen/`（Python 工具）**整体迁址**至 `pg-spring-crud-codegen/`，并以"日后可独立拆出（保留提交历史）"为约束布局：自洽的 README / 依赖文件（`requirements.txt`/`pyproject.toml` 二选一）/ 入口脚本 / 模板目录；对主应用零运行时依赖（仅在开发期手工执行）。

执行方式（推荐 A）：
- **A) 仓内直接迁址 + 路径软兼容**：`git mv scripts/codegen/* pg-spring-crud-codegen/`；同时在 `scripts/codegen/README.md` 留一行"已迁至 `pg-spring-crud-codegen/`"指针，避免内部链接/旧引用 404。日后用 `git filter-repo`（或 `git subtree split`）把 `pg-spring-crud-codegen/` 带历史拆为独立仓。
- **B) 直接 `mv` 不留指针**：更干净，但对现有内部引用（如 [ADR-0004](ADR-0004-layered-backend-codegen.md)、`backend-architecture.md §3`、`engineering/backend-guide.md`）的链接需要全部同步修正——风险/工作量略高。
- **C) 暂不迁址，仅清理空目录** —— 与维护者意向不符，作为回退选项。

> 注：A 与 B 的差异主要在"如何减小破窗成本"，不影响最终态。两者迁址后的目录结构相同。

## 后果（Consequences）

- **正面**：
  - 构建期工具与业务代码边界清晰；主仓不再耦合脚手架。
  - 为后期独立仓拆分铺路，且因是"同一 Python 工具"，迁址不引入工具语言/模板的不连续性。
- **负面 / 代价**：
  - 内部跨文档链接（[ADR-0004](ADR-0004-layered-backend-codegen.md) 等）需同步更新，遗漏会致死链。
  - 即便方案 A 保留软指针，开发者肌肉记忆（`scripts/codegen`）需要适应期。
  - 模板与后端分层约定的同步责任不变；拆为独立仓后版本/标签策略需另立约定。
- **影响范围**：`scripts/codegen/`、`pg-spring-crud-codegen/`、`backend-architecture.md §3`、`engineering/backend-guide.md`、[ADR-0004](ADR-0004-layered-backend-codegen.md)、`CODEOWNERS`。

## 考虑过的备选（Alternatives Considered）

- **Git submodule** — 立即物理分离，但开发期跨仓协作成本上升；与维护者"后期拆出"的渐进路径不匹配，作为后续阶段选项之一。
- **发布为内部 PyPI/CLI 包** — 复用性最佳，但维护开销大；当前规模偏重，暂不必要。
- **保留 `scripts/codegen` + 清理空目录** — 与维护者意向不符。

## 关联（References）

- [ADR-0004](ADR-0004-layered-backend-codegen.md)、[backend-architecture.md §3](../../architecture/backend-architecture.md)、[open-questions.md](../../modernization/open-questions.md)（OQ-ARCH-3）。
- 代码：`scripts/codegen/main.py`、`scripts/codegen/templates/`、（空）`pg-spring-crud-codegen/`。
