# AI Kids Care Project Instructions

This file contains repository-specific instructions. The shared agent behavior is in `CONTEXT.md`. For the engineering harness — the map of Guides/Sensors, how to verify locally without a JDK, and the "recurring issue → add a control" discipline — see [`docs/engineering/harness.md`](../docs/engineering/harness.md).

## Working Language

- Communicate with the maintainer in the language used by the request.
- Durable engineering documentation uses Simplified Chinese with established English technical terms.
- Preserve code identifiers, API paths, enum values, database names, and Korean product copy.

## Repository Map

- `frontend/`: Next.js static export served by Nginx.
- `backend/`: Spring Boot REST API, JPA, Flyway, and Neo4j query adapter.
- `ai/`: FastAPI inference and standalone VideoMAE scripts.
- `db/`: PostgreSQL design/fixtures and Neo4j loader assets.
- `docs/specs/`: approved or proposed behavior before implementation.
- `docs/architecture/`: verified current architecture, not future intent.
- `docs/decisions/adr/`: durable decisions and tradeoffs.
- `docs/assessments/`: date-stamped audits; do not keep appending audits to one timeless file.

## Evidence Rules

- Current behavior: verify code/config/schema/tests first.
- Intended direction: verify accepted ADRs and approved specs.
- An accepted ADR is not proof that implementation exists.
- A passing build is not proof that an end-to-end workflow works.
- Manually maintained endpoint prose is secondary to Controller/OpenAPI and frontend call sites.

## Spec First Workflow

1. Create or update a feature spec from `docs/specs/spec-template.md`.
2. Resolve cross-cutting or hard-to-reverse choices with an ADR.
3. Define acceptance criteria and verification before editing code.
4. Implement in a scoped change.
5. Run the narrowest checks, then affected builds/tests.
6. Update the spec implementation status and any as-built architecture docs.

## Role Ownership

- The Lead / Planner owns normative spec content: scope, requirements, acceptance criteria, non-goals, and implementation sequencing.
- An implementation worker may update implementation status, verification evidence, and as-built notes. It must not silently change normative requirements or expand the approved scope.
- A single-objective implementation has one explicit outcome, acceptance criteria, non-goals, and verification set. A sub-agent is an execution mechanism, not the definition of a task or a reason to abandon the current session.

## Local Checkpoint

Use `.ai/CHECKPOINT.local.md` for substantial, interrupted, or multi-agent work. The file is local-only and must remain ignored by Git.

Keep it concise and use these fields:

```markdown
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
```

Update it after each delivery gate, reviewer result, commit, or material change of direction. On resume, read it after `AGENTS.md`, then re-run compact Git checks before continuing.

## Delivery Gates

1. **Scope Gate:** confirm the approved spec or task, acceptance criteria, non-goals, risk level, baseline, and allowed files before implementation.
2. **Pre-review Gate:** run deterministic repository scans, focused tests, affected builds, `git diff --check`, and a targeted final-diff read. Resolve known issues before requesting final review.
3. **Integration Gate (→ `develop`):** focused local checks pass (`git diff --check`, touched-area tests/build). Sub-agent branches get a fresh-context review (reviewer ≠ implementing session) before merging into `develop`; the Lead integrates trivial changes directly. GitHub Actions runs post-hoc on `develop`; a red build is fixed promptly, not left on the trunk.
4. **Release Gate (`develop` → `main`):** open a `develop` → `main` PR. Passing GitHub Actions (`Backend Java Tests` + `Compose Config`) and code-owner approval are required. A fresh read-only reviewer evaluates against the declared baseline and current worktree; any P0-P3 finding blocks release. Only an explicit `FINAL REVIEW: PASS` plus the maintainer's merge decision releases to `main`.

For SPEC-0001 security work, the pre-review gate must also:

- Enumerate all published Controller/OpenAPI operations while `/api/v1/**` remains `permitAll`.
- Scan public DTO/VO/OpenAPI schemas for S0/S1 raw values and storage representations.
- Compare frontend API call paths with published backend mappings.
- Verify closed paths and authentication failures through explicit response contracts, not accidental protected `/error` dispatch.
- Check that frontend code does not infer tenant, role, or identity from demo IDs, JWT parsing, or browser persistence.

## Branch And CI Workflow

- `develop` is the integration trunk; the Lead/Planner agent commits to `develop` directly. Sub-agents — and any isolated or higher-risk unit the Lead chooses to quarantine — work on `codex/<task>` branches cut from a freshly checked `origin/develop`, one objective per branch, then merge back into `develop`. Use worktree isolation for parallel file changes. See ADR-0020 (`docs/decisions/adr/ADR-0020-branch-protection-release-model.md`).
- Before a sub-agent branch merges into `develop`, a fresh-context review (the Lead agent or a dedicated reviewer sub-agent, never the session that produced the implementation) resolves substantive or security-sensitive findings; the Lead integrates trivial changes directly. Keep each sub-agent branch short-lived and integrated promptly to avoid drift.
- Run the narrowest reliable local checks before pushing to `develop` (`git diff --check`, focused tests/build for the touched area). `develop` is an integration line and may be transiently broken; GitHub Actions runs on every push as a post-hoc signal — it cannot block direct pushes, so fix a red trunk promptly rather than leaving it.
- `main` is the protected release line. Ship `develop` → `main` via PR in small, frequent batches. The release PR requires passing GitHub Actions (`Backend Java Tests` + `Compose Config`), code-owner approval, a fresh independent reviewer's `FINAL REVIEW: PASS`, and the maintainer's merge decision.
- Do not force-push or delete `develop` or `main`. Do not merge `develop` → `main` without the release gate and the maintainer's approval.

Use compact Git evidence by default:

```powershell
git status --short
git diff --name-only
git diff --stat
git diff --check
git diff --cached --check
```

Inspect targeted diff sections instead of printing the complete repository diff.

## Verification Commands

```powershell
# backend — LOCAL loop, no local Java/JDK needed: runs Gradle in a container against the
# host Docker daemon (integration tests use Testcontainers; initdb is copied so it works
# under Docker-out-of-Docker). Requires Docker. Run via Git Bash. First run caches deps.
bash scripts/test-backend.sh                        # full backend suite
bash scripts/test-backend.sh '*NotificationRead*'   # only matching test classes (fast iteration)
bash scripts/test-backend.sh --compile              # compile main+test only (fastest; catches type/MapStruct errors)
# (On a host WITH JDK 21 installed you can instead run:  cd backend ; .\gradlew.bat test )

# frontend
cd frontend
npm.cmd ci --legacy-peer-deps
npm.cmd run lint
npm.cmd run build

# merged production compose model
docker compose -f docker-compose.yml -f docker-compose.prod.yml config
```

Before writing backend tests, JPQL, or insert fixtures, read [`docs/engineering/schema-digest.md`](../docs/engineering/schema-digest.md) (generated: every NOT NULL / UNIQUE / FK / enum — regenerate with `bash scripts/schema-digest.sh` after a migration — CI fails on drift via `schema-digest-drift.yml`) and [`docs/engineering/test-conventions.md`](../docs/engineering/test-conventions.md) (fixture pitfalls: shared-container uniqueness, composite tenant FKs, enum casts) instead of grepping the DDL. Then verify locally with `bash scripts/test-backend.sh '<pattern>'`.

AI scripts currently have no test suite. At minimum, syntax-check changed Python files and add focused tests for new behavior.

## Known Baseline

The current verified assessment is `docs/assessments/2026-06-10-codebase-audit.md`. Treat older modernization assessments as historical snapshots.
