# 工程指南（Engineering）

## 用途

本目录是**开发者的操作手册**：如何在本地运行、如何在各组件中做改动、遵循哪些约定、测试现状如何。它回答"**作为开发者，我该怎么在这个项目里干活**"。

面向读者：要动手改代码、跑项目、加功能的工程师。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [local-development.md](local-development.md) | 本地把各组件跑起来（Docker / 原生进程） |
| [backend-guide.md](backend-guide.md) | 后端约定、代码生成器用法、如何加一个端点 |
| [frontend-guide.md](frontend-guide.md) | 前端约定、API 层、构建注意事项 |
| [ai-guide.md](ai-guide.md) | AI 训练/推理/实时告警的运行方式 |
| [database-guide.md](database-guide.md) | DBML → SQL 工作流、种子数据、Neo4j 加载 |
| [coding-conventions.md](coding-conventions.md) | 跨组件命名与分层约定 |
| [testing.md](testing.md) | 测试现状（当前无自动化测试）与 `CLAUDE.md` 测试规则 |

## 工作模式（来自 `CLAUDE.md`）

每个会话开始时声明工作模式，不要静默切换：

- **Discovery** — 理解系统：分析、记录、提问。不重构、不改行为、不做架构决策。
- **Design** — 评估方案：比较备选、识别权衡、产出 ADR 提案。不实现。
- **Implementation** — 执行已批准的工作：实现单一任务，改动小，更新测试与文档。不做无关重构、不擅自引入架构变更。

## 会话纪律（来自 `CLAUDE.md`）

每个会话应：单一目标、引用相关 ADR、定义成功标准、可独立评审、结束时仓库处于可工作状态。
