# 架构文档（Architecture）

## 用途

本目录从**技术架构视角**描述 AI Kids Care：系统由哪些组件组成、各自的内部结构、它们如何协作、数据如何流动、安全如何设计。它回答"**这个系统是怎么搭起来的**"。

面向读者：需要理解或修改系统的工程师、做技术评审或集成的人。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [system-overview.md](system-overview.md) | 全局视图：monorepo 组成、运行时拓扑、端口、技术栈 |
| [backend-architecture.md](backend-architecture.md) | 后端分层（Controller/Service/Repository）、包结构、关键模式 |
| [frontend-architecture.md](frontend-architecture.md) | Next.js 静态导出、Redux、API 客户端、鉴权处理 |
| [ai-architecture.md](ai-architecture.md) | VideoMAE 推理服务、训练管线、实时告警链路 |
| [data-architecture.md](data-architecture.md) | PostgreSQL + Neo4j 双存储、多租户、数据生命周期 |
| [security-architecture.md](security-architecture.md) | 认证、加密、CORS，及**当前安全态势与风险** |
| [integration-and-dataflow.md](integration-and-dataflow.md) | 组件间集成方式与典型时序流 |

## 架构原则（来自 `CLAUDE.md`）

系统遵循以下原则（除非有 ADR 明确变更）：

- 优先显式依赖，组合优于继承。
- 模块边界清晰，避免隐藏耦合。
- 业务逻辑不放在传输层；框架细节不渗入领域逻辑。
- 可维护性优先于"聪明"。

## 既有架构决策

重大的、已固化在代码中的架构决策，以**回溯性 ADR** 形式记录在 [decisions/adr/](../decisions/adr/README.md)。它们是对**现状**的逆向描述，而非新提案。
