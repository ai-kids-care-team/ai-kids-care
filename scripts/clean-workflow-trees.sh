#!/usr/bin/env bash
# Clean team-pipeline scratch artifacts left behind by isolated-worktree runs.
#
# Removes, safely and deterministically:
#   1. linked git worktrees under .claude/worktrees/      (scratch checkouts)
#   2. harness scratch branches  worktree-wf_*            (only if their tip is
#      already reachable from develop or main — i.e. no unique work)
#   3. deliverable branches  task/* and legacy codex/*    (only if already
#      merged into develop)
#
# NEVER touches develop / main, the main worktree, or UNMERGED deliverable
# branches (those are awaiting the Lead's merge). Re-runnable / idempotent:
# run it at workflow end (clears scratch) and again after merging (clears the
# now-merged deliverable branches).
set -uo pipefail

echo "== clean-workflow-trees =="

# 1) linked worktrees under .claude/worktrees/
git worktree list --porcelain | awk '/^worktree /{print $2}' | grep '/\.claude/worktrees/' | while read -r wt; do
  git worktree remove --force "$wt" 2>/dev/null && echo "worktree removed: $wt"
done
git worktree prune

# 2) harness scratch branches worktree-wf_* (delete only if preserved on develop or main)
for b in $(git for-each-ref --format='%(refname:short)' refs/heads/ | grep -E '^worktree-wf_' || true); do
  if git merge-base --is-ancestor "$b" develop 2>/dev/null || git merge-base --is-ancestor "$b" main 2>/dev/null; then
    git branch -D "$b" >/dev/null && echo "scratch branch deleted: $b"
  else
    echo "WARN keep (has commits not on develop/main, inspect): $b"
  fi
done

# 3) deliverable branches already merged into develop (task/* and legacy codex/*)
for b in $(git for-each-ref --format='%(refname:short)' refs/heads/ | grep -E '^(task|codex)/' || true); do
  if git merge-base --is-ancestor "$b" develop 2>/dev/null; then
    git branch -d "$b" >/dev/null 2>&1 && echo "merged deliverable deleted: $b"
  else
    echo "keep (unmerged deliverable, awaiting Lead merge): $b"
  fi
done

echo "== done =="
