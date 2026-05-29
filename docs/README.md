# AI Kids Care — 工程知识库（Knowledge Base）

> 本目录是 AI Kids Care 项目的**长期工程知识库**。目标：让一个从未接触过本项目的开发者，**仅阅读 `docs/` 目录**，即可理解项目的大部分内容——它的业务目标、系统架构、数据模型、运行方式、关键约束与已知风险。

本知识库不是一次性的重构文档，而是随系统长期演进、持续维护的资产。它由资深架构师在 **Discovery（理解系统）模式** 下，通过通读现有仓库代码逆向梳理而成，**未修改任何业务代码、未重构、未引入新架构**。

---

## 阅读起点（建议顺序）

| 你想了解… | 从这里开始 |
| --- | --- |
| 这个产品是做什么的、给谁用 | [product/](product/README.md) |
| 系统由哪些部分组成、如何协作 | [architecture/system-overview.md](architecture/system-overview.md) |
| 如何在本地把项目跑起来 | [engineering/local-development.md](engineering/local-development.md) |
| 有哪些 API、数据长什么样 | [api/](api/README.md)、[architecture/data-architecture.md](architecture/data-architecture.md) |
| 怎么部署、怎么配置 | [operations/](operations/README.md) |
| 历史架构决策、为什么这样设计 | [decisions/adr/](decisions/adr/README.md) |
| 当前技术债、风险、待确认事项 | [modernization/](modernization/README.md) |

---

## 三级可信度标注（重要约定）

本知识库的每一条非显然结论，都按其**证据强度**分为三级。阅读时请始终注意标注，不要把"推断"当成"事实"。

| 标注 | 含义 | 判定标准 |
| --- | --- | --- |
| ✅ **已确认（Confirmed）** | 能从代码、配置或 schema 直接证明 | 有明确的文件与行号可追溯 |
| 🔶 **推断（Inferred）** | 根据代码高度推测，但缺少直接证据 | 多处迹象一致指向同一结论，但意图未被显式写明 |
| ❓ **待确认（Open Question）** | 需要人工（原作者/团队）确认 | 代码存在矛盾、缺口或意图不明，无法仅凭代码判定 |

> 所有 ❓ 待确认事项都汇总在 [modernization/open-questions.md](modernization/open-questions.md)，这是与团队对齐的核对清单。

---

## 知识库结构

```text
docs/
├── README.md                  # ← 你在这里：总入口、导航、可信度约定
├── product/                   # 产品视角：做什么、给谁、有什么价值
├── architecture/              # 系统架构：组成、分层、数据流、安全
├── decisions/adr/             # 架构决策记录（ADR）：为什么这样设计
├── engineering/               # 工程指南：如何开发、约定、本地运行
├── operations/                # 运维：部署、配置、排障、可观测性
├── modernization/             # 演进：现状评估、技术债、待确认事项、路线图
├── api/                       # 接口契约：REST / AI 服务 / 图查询
└── db/                        # （既有）数据库 ERD 图与图示
```

各目录的用途与详细索引见其各自的 `README.md`。

---

## 一句话项目概览

AI Kids Care 是面向**幼儿园安全管理**的 AI 平台：通过 CCTV 视频流做**异常行为检测**（打斗、跌倒/晕厥、徘徊、闯入等），把检测事件、值班复核、家长/教职工通知、公告、感谢信，以及幼儿园-班级-教室-儿童-保护者的运营数据统一在一个系统里管理。

技术形态为 **monorepo**，包含五块可独立构建的组件：

- **frontend** — Next.js 16 / React 19，静态导出 + Nginx
- **backend** — Spring Boot 3.2.5 / Java 21，REST API
- **ai** — FastAPI + PyTorch（VideoMAE）视频分类与实时告警
- **db** — PostgreSQL 16（关系型）+ Neo4j 5.19（关系图）
- 配套：`scripts/codegen`（代码生成器）、`jenkins`（CI）、`docker-compose`（整栈编排）

详见 [architecture/system-overview.md](architecture/system-overview.md)。

---

## 维护约定

- 本知识库受仓库根目录 [`CLAUDE.md`](../CLAUDE.md) 约束。当文档与代码冲突时，**信息优先级**为：ADR > 架构文档 > 产品文档 > 现有代码 > 假设。
- 文档中引用代码时尽量带 `文件路径:行号`，以便追溯与校验。
- 信息会随时间过期。任何基于本库做决策前，请先核对当前代码状态，发现不一致时**以代码为准并更新文档**。
- 重大决策的变更必须通过新增 ADR，而不是悄悄改写文档。

**基线快照时间：2026-05-29**（首次建立）。本库内容反映该时间点的代码状态。

## 语言说明

知识库正文以**简体中文**为主，技术术语保留英文（如 `Controller`、`JWT`），数据库表名、枚举值、API 路径等标识符保留代码中的原始形式。领域术语的中／韩／英对照见 [product/glossary.md](product/glossary.md)。
