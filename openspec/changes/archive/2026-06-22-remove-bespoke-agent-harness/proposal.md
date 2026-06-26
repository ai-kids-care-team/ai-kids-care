# Remove Bespoke Agent Harness

## Why

仓库已采纳 OpenSpec + superpowers 作为工作范式（Change 0 已落地）。原项目自研的「agent
harness 层」——宪法/指令散文、自研 agent 与 workflow、本机 hook、域/状态技能、工程过程文档——
现已是与 OpenSpec/superpowers 竞争的冗余通用脚手架。移除它，逼近最小化的
「产品代码 + OpenSpec + superpowers」状态。

## What Changes

**BREAKING（仅开发工作流，不触产品运行时代码）：**

- 删除 `.ai/`：`CONTEXT.md`（宪法）、`project.md`、`HANDOFF.local.md`、`CHECKPOINT.local.md`。
- 删除 `.claude/agents/`：`planner.md` / `implementer.md` / `reviewer.md`
  （→ 改用 superpowers 子 agent / 评审技能）。
- 删除 `.claude/workflows/`：`implement-review-loop.js` / `team-pipeline.js`
  （→ superpowers executing-plans / dispatching-parallel-agents）。
- 删除 `scripts/clean-workflow-trees.sh`（team-pipeline worktree 清理脚本，随 workflow 一并失效）。
- 删除 `.claude/hooks/`（`high-risk-task-guard.ps1`、`README.md`）及 `settings.json` 的
  `hooks`（SessionStart / Stop / TaskCreated）与 `env.CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS`。
- 删除 `.claude/skills/authz-read-slice/`、`.claude/skills/checkpoint/`
  （域/状态技能；护栏价值转入 Change 3 的「待重建护栏」backlog）。
- 删除 `docs/engineering/*`（`harness.md`、`test-conventions.md`、`incidents.md`、各
  `*-guide.md`、`coding-conventions.md`、`local-development.md`、`testing.md`、`README.md`）。
  **例外**：`schema-digest.md` 与其 CI/脚本耦合，移至 Change 2 一并删除，避免
  `schema-digest-drift` CI 出现「文档已删但 CI 仍在」的红窗口。
- 更新根 `CLAUDE.md` / `AGENTS.md`：移除失效的 `@.ai/CONTEXT.md` / `@.ai/project.md` import，
  替换为指向 OpenSpec（`openspec/`）+ superpowers 的最小说明。

## Capabilities

本 change 确立「开发流程」这一能力的目标状态（用 OpenSpec + superpowers），并移除旧 harness。
无 *产品* 能力变更。

- **New Capabilities**: `agent-workflow` —— 仓库开发流程：OpenSpec 承载 spec/change 生命周期、
  superpowers 承载执行纪律、agent 指令最小化并委派给二者。
- **Modified Capabilities**: 无

## Impact

- 影响面：开发工作流 / agent 指令层。**不触** `backend/src/main`、`frontend/`、`ai/`、`db/`
  等产品代码与运行时。
- 跨 change 顺序：与 Change 2（tests + CI）存在耦合项（schema-digest 三件套、
  `scripts/test-backend.sh`、Backend Java Tests CI、`build.gradle`），一律归 Change 2，以保证
  本 change apply 后 CI 不出红窗口。建议 Change 1 → Change 2 连续 apply。
- 风险：删除自研 risk-halt（hook + workflow）后，高风险闸门强制力由「代码」回退到
  「人 + 分支保护 + superpowers 纪律」。已知并接受的权衡。
