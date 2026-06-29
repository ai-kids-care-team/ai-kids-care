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
| 2026-06-25 | gitignore 静态复核陷阱告警（前端在 .gitignore，Grep/Glob 静默跳过→须裸 rg 绕过）；新增 Phase 4b 验证跑模式（打分前 lead 重核真值存活态 + 精度/召回/净增三栏记分卡） | analyze-integration + adversarial-verification + component-analysis-orchestrator | backend 验证跑实证：工具忽略前端致「未接线」误判，陈旧真值污染召回 |
| 2026-06-25 | 框架自动装配陷阱：修正「缺 Executor bean ⇒ SimpleAsyncTaskExecutor」误判，明确 Boot TaskExecutionAutoConfiguration 默认池；「缺 bean→默认行为」类推断优先送 DooD 实测机制 | analyze-performance | 深度档 DooD 实跑证伪 PRF-01 失效机制（实为无界队列堆积非线程爆炸） |
| 2026-06-26 | 新增发版前双层验收:release-visual-validator(第 9 个 agent)+ release-visual-acceptance skill(Tier-1 真人体验官,Playwright MCP)+ 仓库根 e2e/ 确定性 Playwright(Tier-2,release.yml 硬门禁) | 新 agent/skill + e2e/ + release.yml + .mcp.json | 发版前需真人体验把关 + 功能硬门禁,两层解耦 |
| 2026-06-29 | harness 整改(OpenSpec change `fix-analysis-harness-design-flaws`,刻意不归档):①**退役 release-visual-validator + release-visual-acceptance**(盲探索又慢又高假阴险、当前不可执行),可发现性/无死胡同断言下沉 Tier-2 e2e/;②编排器删除不存在的 `TeamCreate`/「实时互证」措辞,改诚实 fan-out DAG + 执行拓扑路由 + 验证并行;③校正化石假设(node 已在 PATH、frontend 未被 gitignore);④新增本地 pre-push lint 预检 | orchestrator + analysis-lead + adversarial-verification + 删 validator/acceptance + e2e/ + .githooks/ + 本文件 | 隔离 sub-agent 审计:成文设计透支实际工具与环境能力(TeamCreate 不存在致三档静默降级、真人体验官跑不起来、lint 到 CI 才红、过期假设污染验证) |
