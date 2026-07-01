---
name: gc
description: 回收本地残留的 git worktree 与 worktree-agent 分支——列清单、按合并状态分级、人确认后删除。当用户要"清理 worktree / 清理分支 / 收垃圾 / gc / 任务结束后清残留 / 删掉用不上的 agent worktree"时使用。
---

# gc — 本地 worktree / 分支垃圾回收

任务（尤其并行 subagent、worktree 隔离）结束后，本地常残留 `.claude/worktrees/agent-*` 与 `worktree-agent-*` 分支。本 skill **列清单 → 按合并状态分级 → 人确认 → 删除**，尊重 CLAUDE.md「破坏性逐个批准」。

**Announce at start:** "我在用 gc skill 回收本地 worktree / 分支残留。"

## 边界（不可违背）
- **绝不删主仓库工作树**、`develop`、`main`、当前所在分支。
- **绝不静默删除**：任何删除前必须把清单呈现给用户、等确认。
- **未合并 / 工作树脏的 worktree** 默认**不删**，红标列出；用户显式点名要删才走 `-D` 强删并二次确认。
- 已合并进 `develop`（或 `main`）且工作树干净的，是删除的**默认安全候选**。
- 孤儿 `;W` 目录由 SessionStart 的 `gc-orphan-worktrees.ps1` hook 自动清；本 skill 兜底处理 hook 因脏改动而保留的那些。

## 流程

### 1. 盘点
```bash
git worktree prune                       # 先清失效注册项
git worktree list --porcelain            # 注册中的 worktree
git branch -vv                           # 本地分支 + 上游
git for-each-ref --format='%(refname:short)' refs/heads/   # 全部分支名
```
对每个 `worktree-agent-*` 分支 / `.claude/worktrees/agent-*` worktree，判定：
- **合并状态**：`git branch --merged develop` 是否含该分支（亦查 `main`）。
- **工作树是否干净**：`git -C <worktree-path> status --porcelain` 是否为空。
- **领先提交**：`git log --oneline develop..<branch>`（让用户看到将丢什么）。

### 2. 分级呈现
分三档列给用户：

```
可安全回收（已合并进 develop + 工作树干净）：
  - worktree-agent-XXXX  @ .claude/worktrees/agent-XXXX   [merged, clean]

需确认（未合并 / 工作树脏 —— 删除会丢工作）：
  - worktree-agent-YYYY  @ .claude/worktrees/agent-YYYY   [ahead N commits / dirty]
    将丢失：<git log --oneline develop..YYYY 摘要>

主仓库 / 受保护，不动：
  - develop, main
```

问：「回收"可安全回收"全部？"需确认"项要逐个处置吗？」

### 3. 删除（仅对用户批准项）
复用 `finishing-a-development-branch` 的防坑顺序——**先 cd 主仓库根，先删 worktree 再删分支**：
```bash
MAIN_ROOT=$(git rev-parse --show-toplevel)   # 确保不在待删 worktree 内
cd "$MAIN_ROOT"
git worktree remove "<worktree-path>"        # 干净则成功；脏需用户确认后加 --force
git worktree prune
git branch -d "<branch>"                      # 已合并；未合并经二次确认才 -D
```
- worktree 干净 → `git worktree remove`；脏且用户已确认丢弃 → `--force`。
- 分支已合并 → `git branch -d`；未合并且用户已二次确认 → `git branch -D`。

### 4. 收口
重跑 `git worktree list` + `git branch -vv`，把删了什么、留了什么回报给用户。

## 常见坑（来自 finishing-a-development-branch）
- **在待删 worktree 目录内跑 `git worktree remove`** → 静默失败。务必先 `cd` 主仓库根。
- **先删分支再删 worktree** → `git branch -d` 因 worktree 仍引用而失败。顺序：worktree 在前。
- **未确认就强删** → 丢未合并工作。`-D` / `--force` 必须经用户显式点头。
