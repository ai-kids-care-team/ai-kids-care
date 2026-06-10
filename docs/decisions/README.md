# 决策记录（Decisions）

## 用途

本目录承载 **ADR（Architecture Decision Records，架构决策记录）**——用轻量、可追溯的方式记录"为什么这样设计"。

ADR 是**意图和约束**的权威来源，但不是当前实现已经存在的证明。当前行为必须回到代码、配置、迁移、测试与运行证据核验。

ADR 全部位于 [adr/](adr/README.md)。

## 为什么需要 ADR

- 架构决策的**理由**往往随时间流失。代码只能告诉你"是什么"，ADR 记录"为什么"以及"放弃了哪些备选"。
- 让新人快速理解约束的来龙去脉，避免无意中推翻有意为之的设计。
- 提供变更的正式通道：**要改变一个已记录的决策，应提出新的 ADR**，而不是悄悄改写文档或代码。

## 何时需要写 ADR（来自 `CLAUDE.md`）

重大决策需要 ADR：架构风格、数据库策略、模块边界、消息策略、API 兼容性变更。实现细节不需要。

ADR 标题格式：英文 ID + 描述性标题，例如 `ADR-0005: Introduce Repository Layer`。

## 流程

1. 复制 [adr/adr-template.md](adr/adr-template.md) 为 `ADR-XXXX-标题.md`（XXXX 取下一个序号）。
2. 填写：背景 / 决策 / 后果 / 备选。状态初始为 `Proposed`。
3. 评审后将决策状态改为 `Accepted`（或 `Rejected`）。
4. 用独立的 `implementation` 字段跟踪 `Not Started / Partial / Complete`，不要把 `Accepted` 当成 `Implemented`。
5. 被后续 ADR 取代时，状态改为 `Superseded` 并填写 `superseded_by`。
6. 在 [adr/README.md](adr/README.md) 索引中登记。

## 关于"回溯性 ADR"

本知识库首次建立时（2026-05-29），系统**已有大量既成的架构决策固化在代码里，但从未写成 ADR**。为补全上下文，我们以**回溯性（Retrospective）ADR** 的形式逆向记录这些**既有**决策（ADR-0001 起），它们：

- 描述**现状（as-built）**，是观察与推断，**不是新提案**；
- 其"决策理由"凡无法从代码证明的，均标注为 🔶 推断或 ❓ 待确认；
- 不构成对现状的背书或反对——仅为帮助新人理解。
