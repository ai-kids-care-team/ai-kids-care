# AI Kids Care

This repository uses **OpenSpec** for specs and change proposals — see `openspec/`
(project context lives in `openspec/config.yaml`) — and the **superpowers** skills for
execution discipline (planning, TDD, verification, code review, worktrees).

Start a change with `/opsx:propose "<idea>"`; implement with `/opsx:apply`.

## Harness：组件多角度分析（Component Multi-Angle Analysis）

**目标：** 对 backend / frontend / ai / db / infra 各组件，从架构、质量、安全、集成、性能、用户六个角度并行审查，经对抗式验证后综合成决策者可读的报告。

**触发：** 当请求涉及"分析工程/组件、多角度分析、代码审查/健康度评估、架构/安全/质量/集成/性能/用户体验审查"，或"重新分析、再跑一遍、更新分析、只重看某组件、基于上次结果改进"时，使用 `component-analysis-orchestrator` skill。可指定运行档位：**快扫/快速体检 → 轻量**（fan-out 无验证）；默认 **标准**（agent 团队 + 静态验证）；**深度/发版前/关键审计 → 深度**（团队 + 多票 + DooD 实跑）。单纯问答可直接回答。区别于 `/code-review`（仅看当前 diff）——本 skill 审查组件/工程现状。

**模型分配：** 基线 sonnet，重推理角色上 opus —— security-analyst、integration-analyst、performance-analyst、experience-analyst、finding-verifier、analysis-lead = opus；architecture-analyst、quality-analyst = sonnet。

**语言约定：** lead 对用户输出以中文为主、英文为辅；团队内部 SendMessage 可用英文以保语义精确。

**变更历史：**
| 日期 | 变更内容 | 对象 | 原因 |
|------|----------|------|------|
| 2026-06-25 | 初始构建（5 agents + 5 skills） | 全部 | 组件多角度分析需求 |
| 2026-06-25 | 模型按推理负载分配（非一刀切 sonnet）；文档改中文为主、去韩文 | 各 agent + 本文件 + orchestrator | 用户反馈：重推理角色用 opus；文档以中文为主英文为辅 |
| 2026-06-25 | v2：6 角度（+performance/+experience）+ 对抗式 finding-verifier + 三档运行（轻量/标准/深度） | 全部（8 agents + 8 skills） | 首跑短板：可信度/角度粒度/验证手段/视角单一 |
