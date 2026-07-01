# Fan-in 收口 Playbook

> 两个 implementer 在各自 worktree 并行产出后,dev-lead 按本 playbook 把提交收口到 `develop`,再进门禁。

## Fan-out 前提回顾

- 每个 implementer 在**独立 git worktree**(`.claude/worktrees/`)工作,互不踩踏。
- 单条 change 默认 **1 后端 lane + 1 前端 lane**;**纯后端 / 纯前端** change 则只开一侧。
- 每个实现者 `Agent` prompt 自包含:负责组件、change 路径、**冻结契约路径**、需实现的 tasks 子集、TDD 要求、本侧关键约定。
- 实现者**彼此独立、不互相通信**(本环境无 `TeamCreate`,无实时互通);跨侧疑问记进各自产出的 notes,由 dev-lead 在 fan-in 核对。

## 收口步骤

1. **每批开工前核对 `git status`** —— 踩踏防护:在**有未提交改动的 worktree** 上再跑 implement/fix 轮,前一批会被 fix 轮 clobber 回 HEAD(记忆:workflow 脏 worktree 会 clobber)。开工即确认干净或先提交。
2. **批次间先 commit,再进下一轮**门禁/修复。
3. **cherry-pick 到 `develop`**:把两个 worktree 的提交按 lane cherry-pick 过来;沿用记忆里已跑通的 parallel-apply 收口手法。
4. **冲突裁决**:前后端文件域基本不重叠(`backend/` vs `frontend/`),冲突罕见;若确有重叠(如共享的 openspec change 文件),**优先保契约一致侧**,由 dev-lead 裁决,不单方丢弃任一侧。
5. **cherry-pick 后立即进 gate-checklist ①硬测试门** —— 合并后的 develop 状态才是门禁对象,不是单个 worktree。

## worktree 卫生

- 收口后按 finishing-a-development-branch / `/gc` 三层机制清理残留 worktree/分支:SessionStart hook 自动 `prune` + 清空孤儿目录;`/gc` skill 带确认回收。
- **判定用 `git rev-parse --show-toplevel` == 自身**:`git status` 对无 `.git` 目录会跳到父仓库误判,故以 toplevel 等于自身路径为准。

## exhausted 兜底

- 门禁回路耗尽仍未清零时,**末轮 implementer fix 常留工作树未提交**(记忆:loop exhausted 末轮 fix 未提交)→ dev-lead **必须自验 + 提交**,不能假设已提交。
- 未清零项在收口报告如实标注,交维护者定夺,不静默放行。
