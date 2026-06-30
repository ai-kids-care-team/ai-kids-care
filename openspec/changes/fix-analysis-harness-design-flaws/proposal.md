## Why

自建的「组件多角度分析」harness 与「发版前视觉验收」存在一类共同病根：**成文设计透支了实际工具与环境能力**——核心机制要么依赖本环境不存在的工具，要么依赖已失效的环境前提，处于「看着很美、实际跑不动或已悄悄降级」的状态。一次隔离 sub-agent 审计（2026-06-29）核实出多处实锤问题。本提案把审计结论沉淀为可执行的修订契约。

本提案为 harness（元基础设施）维护，**与业务能力无关，实现后不归档**（delta spec 不并入 `openspec/specs/` 业务能力），仅作为一次受控、可追溯的 harness 进化记录。

## What Changes

- **编排器执行模型诚实化**：`component-analysis-orchestrator` 与 `analysis-lead` 当前调用本环境不存在的 `TeamCreate`，导致标准/深度档静默降级为 fan-out、三档差异退化为「跑不跑验证」。改写为显式 **fan-out + lead 二次交叉合并** 的 DAG，删除「真团队/实时互证」措辞。**BREAKING**（编排语义变更）。
- **引入执行拓扑路由**：新增「拓扑选择」阶段，依据任务依赖图决定哪层并行、哪层串行（与运行档位正交）。验证阶段由单 verifier 串行改为按 finding 并行多 verifier，DooD 类单独排队。
- **release-visual-validator 去留决策与收口**：该角色「无预知盲探索」导致必然又慢又找不到入口，且当前 `.mcp.json` 已被改空而 `settings.local.json` 仍 enable `playwright`，处于跑不起来的半截状态。本提案先收口配置一致性，并把其唯一独特价值（可发现性、流程无死胡同）下沉为 Tier-2 `e2e/` 的确定性断言，将该角色降级为非阻塞限时探针或退役。
- **新增本地 pre-push 预检**：消灭「lint 等到 CI 才红」。版本化 `.githooks/pre-push`（POSIX sh，走 Git Bash）+ `core.hooksPath`，前端改动本地 `eslint` 拦截，`node:20` docker 作回退。
- **校正过期「化石假设」**：核实 node v24 已在 PATH（「本机无 node」前提失效）、`frontend/` 未被 gitignore（`adversarial-verification` 里那条 ⚠️ 警告是错误前提，污染验证校准），更新相关 skill/笔记/CLAUDE.md 文案。
- **CLAUDE.md 与配置一致性修补**：模型分配表补齐第 9 个 agent（release-visual-validator）；明确 `_workspace/` 产物的时效判定与 `.gitignore` 覆盖；收窄 `component-analysis-orchestrator` 触发词以与 `harness:harness`/`/code-review` 区隔。

## Capabilities

### New Capabilities
- `analysis-harness`: 组件多角度分析 harness 的执行契约——编排器的执行拓扑（fan-out DAG、拓扑路由、验证并行）、配置一致性（MCP/settings）、化石假设校正、agent 职责边界与模型分配的一致性要求。
- `local-preflight`: 提交/推送前的本地确定性预检契约——前端 lint 在 pre-push 阶段本地拦截，在「hook 解释器仅 git/powershell、可能无 node」约束下可落地并可随仓库分发。

### Modified Capabilities
<!-- 无：本提案刻意不修改任何业务能力 spec（与业务无关，不归档）。 -->

## Impact

- **Harness 资产**：`.claude/skills/component-analysis-orchestrator/`、`.claude/skills/adversarial-verification/`、`.claude/agents/analysis-lead.md`、`.claude/agents/release-visual-validator.md`、`.claude/skills/release-visual-acceptance/`。
- **配置**：`.mcp.json`、`.claude/settings.local.json`、`.claude/settings.json`（可选 PowerShell Stop hook）。
- **仓库纪律资产**：新增 `.githooks/pre-push` + `git config core.hooksPath`；`e2e/`（下沉可发现性/无死胡同断言）；`.gitignore`（`_workspace/`）。
- **文档**：`CLAUDE.md`（Harness 节模型表与变更历史）、`docs/superpowers/notes/*`（node/gitignore 化石假设）。
- **不影响**：任何 `backend/`、`frontend/`、`ai/`、`db/` 业务代码与 `openspec/specs/` 业务能力。
