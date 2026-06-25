@.ai/CONTEXT.md
@.ai/project.md

## 하네스: 组件多角度分析（Component Multi-Angle Analysis）

**목표:** backend/frontend/ai/db/infra 各组件从架构·安全·质量·集成四角度并行审查，综合成决策者可读的报告。

**트리거:** 分析工程/组件、多角度分析、代码审查/健康度评估、架构/安全/质量/集成审查，或"重新分析/再跑一遍/更新分析/只重看 X 组件/基于上次结果改进"等请求时，使用 `component-analysis-orchestrator` skill（agent 团队模式：4 分析师 sonnet 4.6 + analysis-lead opus）。单纯问答可直接回答。区别于 `/code-review`（仅看 diff）——本 skill 审查组件/工程现状。

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-25 | 초기 구성 (5 agents + 5 skills) | 전체 | 多角度组件分析需求；成员模型按用户要求用 sonnet 4.6（lead 仍 opus，偏离 harness 默认 opus-only） |
