---
name: checkpoint
description: Save or resume a local execution checkpoint (.ai/CHECKPOINT.local.md) for long / multi-session / post-compaction work — snapshots git state, what's done, what's pending, the next exact action, and the active follow-up backlog. Use "/checkpoint" (or "/checkpoint save") to write, "/checkpoint resume" to reload and re-orient.
---

维护 `.ai/CHECKPOINT.local.md`（**本地、已被 `.gitignore` 忽略**），让长任务在跨会话 / 被 compaction 续接 / 隔几天回来时**不丢进度与 follow-up**。

按用户传入参数选模式：无参 / `save` / `update` → **写入**；`resume` / `read` → **读回**。

---

## 写入模式（默认）

1. 采集实时 git 状态：
   - `git rev-parse --abbrev-ref HEAD`（分支）
   - `git log --oneline -1`（HEAD）
   - `git status --short`（工作区）
   - 基线：`git merge-base HEAD origin/develop`，或当前任务的起点 commit
2. 结合当前对话进度，按下方模板**覆盖写入**整个文件（保持字段顺序）；未知字段填 `—`，日期用绝对日期。
3. 写完打印一行确认：文件路径 + `Next exact action`。

模板：

```markdown
# CHECKPOINT (local) — updated: YYYY-MM-DD

Mode:
Objective:
Baseline commit:
Branch / HEAD:
Current stage:
Completed:
Pending:
Verification:
Reviewer findings:
Commit allowed: yes/no
Next exact action:
Follow-up backlog:
```

规则：
- **绝不** `git add` 本文件（本地专用，已忽略）。
- `Completed` / `Pending` 尽量用 backlog 的 ID（如 `DB-1`、`BE-1`）引用，不重复正文。
- `Next exact action` 必须是一条**可直接执行的具体动作**，不是"继续"。
- `Follow-up backlog` 指向已提交的清单（如 `docs/assessments/2026-06-18-followup-backlog.md` 的某个 Wave/ID）。
- 在每个**交付门 / reviewer 结果 / commit / 方向变更**后更新——呼应 `.ai/project.md`「Local Checkpoint」与 `.ai/CONTEXT.md`「Long-Running Work」：context budget ≤ 40% 时务必先更新本文件再 handoff。

---

## 读回模式（`resume`）

1. 读 `.ai/CHECKPOINT.local.md`；若不存在，告知并建议先 `/checkpoint`。
2. 重新核验 git：当前 `branch`/`HEAD`/`status` 是否与 checkpoint 一致；`origin` 是否前进（漂移检测）。
3. 若指向了 backlog 文档，读取其活跃 Wave / 项。
4. 用 3–5 行向用户复述：上次 `Objective`、`Current stage`、`Next exact action`、以及是否有漂移（HEAD/分支变化、worktree 是否过时）。然后等指令或按 `Next exact action` 继续。

---

## 与其它机制的关系

- **SessionStart hook** 已配置为每次开会话自动打印本文件 → "读回"多数时候**自动发生**；`/checkpoint resume` 用于手动重定向或核验漂移。
- **持久 follow-up 清单**放在已提交的 `docs/assessments/*-backlog.md`；本 checkpoint 只记"当前执行到哪 + 下一步"并指向它，两者分工：checkpoint = 易失的执行游标，backlog = 耐久的计划。
