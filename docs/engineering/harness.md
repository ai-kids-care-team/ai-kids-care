# The harness (how work is engineered here)

This is the **map of the agent harness** for this repo: the controls around an AI coding
agent that make changes reliable, and how to use them. It complements — does not repeat —
the behavioural rules in [`.ai/CONTEXT.md`](../../.ai/CONTEXT.md) (the constitution) and
[`.ai/project.md`](../../.ai/project.md) (repo specifics). Read those for *what to do*;
read this for *the system that keeps it correct*.

## The model

A control is either a **Guide** (feedforward: prevents a mistake before the agent acts) or
a **Sensor** (feedback: catches a mistake after, for self-correction). Each is either
**Computational** (deterministic, fast, reliable — tests, lint, schema introspection) or
**Inferential** (semantic, slower, non-deterministic — docs, review agents). You need both
kinds: feedforward-only repeats mistakes silently; feedback-only never learns the rule.

Two operating principles:
- **Keep quality left** — push every check as early/cheap as possible (local before CI,
  CI before release). The earlier a problem is found, the cheaper it is.
- **Recurring issue → add a control** — when the *same class* of mistake happens twice, add
  a Guide or Sensor so it can't recur. This is how the harness improves. (See "Maintenance".)

## This repo's harness, mapped

| Control | Kind | Lives in | Catches / prevents |
| --- | --- | --- | --- |
| Constitution (modes, risk gates, change protocol) | Guide · inferential | `.ai/CONTEXT.md` | unscoped/over-risky changes; skipping plan/verify |
| Repo specifics (evidence rules, spec-first, role ownership, delivery gates, branch/CI) | Guide · inferential | `.ai/project.md` | drift, wrong process, unsafe merges |
| ADRs / specs | Guide · inferential | `docs/decisions/adr`, `docs/specs` | re-litigating decided choices; building without acceptance criteria |
| **Schema digest** (every NOT NULL / UNIQUE / FK / enum) | Guide · computational | `docs/engineering/schema-digest.md` (generated) | blind fixtures/JPQL; the composite-FK / UNIQUE(phone) / enum-cast traps |
| **Test conventions** | Guide · inferential | `docs/engineering/test-conventions.md` | the recurring integration-test fixture failures |
| **Authz read-slice skill** | Guide · inferential | `.claude/skills/authz-read-slice/` | re-deriving the authz read-slice pattern (action+gate / scoped JPQL / VO / tests / 3 contract edits) |
| **Local backend test loop** | Sensor · computational | `scripts/test-backend.sh` | compile/type/MapStruct + runtime test failures, *before push* |
| `git diff --check`, focused tests/build | Sensor · computational | run each change | whitespace, touched-area regressions |
| **Stop hook** (`git diff --check`) | Sensor · computational | `.claude/settings.json` | whitespace / conflict-marker errors — automatically, at turn end |
| **Schema-digest drift check** | Sensor · computational | `.github/workflows/schema-digest-drift.yml` | a stale schema digest after a migration (regenerates in CI, fails on diff) |
| **Test-fixture phone uniqueness** | Sensor · computational | backend test `TestFixturePhoneUniquenessTest` | a full phone literal reused across test classes (the shared-container UNIQUE collision) |
| **Spec acceptance coverage map** | Guide+Sensor · mixed | backend test `SpecAcceptanceCoverageTest` | §372/§390 coverage claims silently rotting when a covering test is deleted/renamed |
| **Incident ledger** | Guide · inferential | `docs/engineering/incidents.md` | re-deriving already-fixed mistakes; losing the "issue → control" loop |
| CI (Backend Java Tests · Compose Config · Frontend lint/build) | Sensor · computational | GitHub Actions | full-suite + compose + frontend, on every push to `develop` |
| Release gate | Sensor · mixed | develop→main PR | 3 required checks + fresh reviewer + maintainer merge |
| Fresh independent reviewer (`FINAL REVIEW`) | Sensor · inferential | release gate; sub-agent merges | semantic defects a fresh context catches |
| Production deploy gate (OQ-3) | Sensor · human | GitHub `production` environment | accidental prod deploy — `:prod` push pauses for approval |
| Checkpoint (progress file) | Memory | `.ai/CHECKPOINT.local.md` (git-ignored) | losing state across sessions |
| Persistent memory | Memory | `~/.claude/.../memory/` + `MEMORY.md` | re-learning durable facts/preferences |
| Sub-agent + Lead review | Orchestration | Agent tool (sonnet) → Lead integrates | context bloat; implementer == reviewer |
| CD pipeline | Orchestration | `release.yml` + watchtower (ADR-0022) | manual, drifty deploys |

## The fast loop (how to actually work here)

1. **Understand before editing.** Read the spec/ADR + relevant code. For fixtures or
   queries, open the **schema digest** and **test conventions** — do not grep the DDL.
2. **Edit one scoped objective.**
3. **Verify locally — this machine has no JDK, so use the container loop:**
   - `bash scripts/test-backend.sh --compile` — fastest; catches compile/type/MapStruct.
   - `bash scripts/test-backend.sh '*YourTest*'` — run the affected class (~1.5 min).
   - `bash scripts/test-backend.sh` — full backend suite before a risky push.
   - `git diff --check` (also auto-run as a Stop hook); compose `docker compose ... config`; frontend lint/build.
4. **Push to `develop`** (the integration trunk). CI re-runs the suite as a post-hoc signal;
   fix a red trunk promptly. (Lead pushes `develop` directly; sub-agents use `codex/<task>`
   branches + a fresh-context review — see project.md.)
5. **Release** `develop → main` via PR: 3 required checks + a fresh reviewer `FINAL REVIEW:
   PASS` + the maintainer's merge. Then tag `vX.Y.Z` → `release.yml` builds + smoke-gates +
   (after the **production** approval) promotes `:prod`, which watchtower deploys.

The point: behaviour verification now lives **locally** (step 3), not only in CI. Use it.

## Maintenance (how the harness grows)

When a mistake reaches CI or review, don't just fix it — **add the control that would have
caught it**, choosing by kind:

- A bad value/shape the schema would reveal → it's already in the **schema digest**; if a
  migration changed it, regenerate: `bash scripts/schema-digest.sh`.
- A recurring *fixture/test* failure mode → add a line to **test-conventions.md** (and a
  regression test where practical).
- A product regression → a focused **test** (a binary, reliable Sensor).
- A decided tradeoff or cross-cutting rule → an **ADR** or a line in the constitution.

Prefer computational controls (deterministic) for structural facts; reserve inferential
controls (review agents) for semantic judgement. Keep each control as far left as it can run.

## Known gaps (where the harness is still weak)

Honest backlog, roughly highest-leverage first:

- **Local hooks are minimal by necessity.** On this Windows + packaged-desktop-app setup the
  only interpreters reliably on a hook's PATH are `git` and `powershell` — `node` ships inside
  the app (not on PATH) and `bash` resolves to WSL, so `node`/`bash` hook scripts are inert or
  wrong (see [`.claude/hooks/README.md`](../../.claude/hooks/README.md)). The one enforced
  local hook is therefore the portable, interpreter-free Stop hook `git diff --check`
  (`.claude/settings.json`). The protected-push guard is left to server-side branch protection
  (the real control); the migration→schema-digest reminder is enforced in CI instead. A richer
  PreToolUse/Stop suite would need a PowerShell (Windows-only) or guaranteed-`node` rewrite.
- **No agent-behaviour eval runner.** The incident ledger ([`incidents.md`](incidents.md)) plus
  the runnable guardrail tests it links (`TestFixturePhoneUniquenessTest`, the contract tests, the
  schema-digest drift check) give deterministic pass/fail coverage of the failure *classes* we've
  actually hit — but there's no harness that replays scenario prompts against an agent with an
  LLM-as-judge verdict. That's a separate, larger effort.
- **Spec acceptance is only partly machine-anchored.** §372/§390 now have a version-controlled
  coverage map (`SpecAcceptanceCoverageTest`) whose referenced tests must exist, so a deleted or
  renamed covering test breaks the build instead of rotting silently. Still human-judged: whether
  the mapped tests *semantically* cover each dimension (the spec retains maintainer sign-off).

Recently closed: the **`/authz-read-slice` skill** codifies the authz read-slice pattern; the
schema digest is enforced in CI (**`schema-digest-drift.yml`**); the shared-container UNIQUE
collision and the "issue → control" discipline are now a runnable guard + ledger
(**`TestFixturePhoneUniquenessTest`**, **`incidents.md`**).

---

*Framing drawn from [Fowler — Harness engineering](https://martinfowler.com/articles/harness-engineering.html),
[Anthropic — Effective harnesses for long-running agents](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents)
and [Effective context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents).*
