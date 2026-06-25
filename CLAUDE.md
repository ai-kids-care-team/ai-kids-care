@.ai/CONTEXT.md
@.ai/project.md

## Harness：组件多角度分析（Component Multi-Angle Analysis）

**目标：** 对 backend / frontend / ai / db / infra 各组件，从架构、安全、质量、集成四个角度并行审查，综合成决策者可读的报告。

**触发：** 当请求涉及"分析工程/组件、多角度分析、代码审查/健康度评估、架构/安全/质量/集成审查"，或"重新分析、再跑一遍、更新分析、只重看某组件、基于上次结果改进"时，使用 `component-analysis-orchestrator` skill（agent 团队模式）。单纯问答可直接回答。区别于 `/code-review`（仅看当前 diff）——本 skill 审查组件/工程现状。

**模型分配：** 基线 sonnet，重推理角色上 opus —— security-analyst、integration-analyst、analysis-lead = opus；architecture-analyst、quality-analyst = sonnet。

**语言约定：** lead 对用户输出以中文为主、英文为辅；团队内部 SendMessage 可用英文以保语义精确。

**变更历史：**
| 日期 | 变更内容 | 对象 | 原因 |
|------|----------|------|------|
| 2026-06-25 | 初始构建（5 agents + 5 skills） | 全部 | 组件多角度分析需求 |
| 2026-06-25 | 模型按推理负载分配（非一刀切 sonnet）；CLAUDE.md 与文档改中文为主、去韩文 | 各 agent + 本文件 + orchestrator | 用户反馈：重推理角色用 opus；文档语言以中文为主英文为辅 |
