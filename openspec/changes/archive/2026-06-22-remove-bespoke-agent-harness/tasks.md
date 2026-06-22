## 1. 前置门（维护者审批）

- [x] 1.1 维护者审阅本 change 的 proposal + tasks，批准执行删除（破坏性，apply 前必须）

## 2. 删除自研 agent 执行层

- [x] 2.1 删除 `.claude/agents/{planner,implementer,reviewer}.md`
- [x] 2.2 删除 `.claude/workflows/{implement-review-loop,team-pipeline}.js`
- [x] 2.3 删除 `.claude/hooks/`（`high-risk-task-guard.ps1`、`README.md`）
- [x] 2.4 删除 `scripts/clean-workflow-trees.sh`（team-pipeline worktree 清理脚本）

## 3. 清理 settings.json

- [x] 3.1 移除 `hooks`（SessionStart / Stop / TaskCreated）三段
- [x] 3.2 移除 `env.CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS`
- [x] 3.3 保留 `enabledPlugins.superpowers` 与 OpenSpec 相关项不动
      （`permissions.allow` 中 `scripts/test-backend.sh` 项随脚本在 Change 2 删除）

## 4. 删除域/状态技能

- [x] 4.1 删除 `.claude/skills/authz-read-slice/`
- [x] 4.2 删除 `.claude/skills/checkpoint/`

## 5. 删除 .ai 指令层

- [x] 5.1 删除 `.ai/CONTEXT.md`、`.ai/project.md`、`.ai/HANDOFF.local.md`
- [x] 5.2 删除本地 `.ai/CHECKPOINT.local.md`（git-ignored，本地文件系统删除）

## 6. 删除工程过程文档

- [x] 6.1 删除 `docs/engineering/*`，**保留** `schema-digest.md`（移交 Change 2）

## 7. 修根指令文件

- [x] 7.1 `CLAUDE.md`：移除 `@.ai/*` import，替换为最小指针（OpenSpec + superpowers）
- [x] 7.2 `AGENTS.md`：同上

## 8. 验证与提交

- [x] 8.1 grep 确认无残留引用：本 change 范围内已清；后端 `harness/*` 测试、`build.gradle:54`、`docs/*` 中的悬挂引用分属 Change 2 / Change 3，预期内
- [x] 8.2 `git diff --check` 干净；产品代码、compose、本 change 范围外的 CI 未受影响
- [x] 8.3 提交到 develop
