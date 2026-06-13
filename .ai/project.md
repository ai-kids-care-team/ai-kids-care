# AI Kids Care Project Instructions

This file contains repository-specific instructions. The shared agent behavior is in `CONTEXT.md`.

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
3. **Release Gate:** use a fresh read-only reviewer against the declared baseline and current worktree. Any P0-P3 finding blocks release. Only an explicit `FINAL REVIEW: PASS` permits the task's authorized commit, push, PR, or merge step.

For SPEC-0001 security work, the pre-review gate must also:

- Enumerate all published Controller/OpenAPI operations while `/api/v1/**` remains `permitAll`.
- Scan public DTO/VO/OpenAPI schemas for S0/S1 raw values and storage representations.
- Compare frontend API call paths with published backend mappings.
- Verify closed paths and authentication failures through explicit response contracts, not accidental protected `/error` dispatch.
- Check that frontend code does not infer tenant, role, or identity from demo IDs, JWT parsing, or browser persistence.

## Branch And CI Workflow

- Start new implementation work on `codex/<task>` from a freshly checked `origin/develop` unless an existing dirty worktree requires an explicitly documented exception.
- Keep each branch and PR centered on one objective. Do not mix unrelated files into a commit.
- Run focused local checks before creating a checkpoint commit. A task-branch push may be used to run GitHub Actions only when the user or task authorizes it.
- Open a PR into `develop` and use GitHub Actions as the shared full-test record. Do not merge, force-push, or push directly to `develop` before the release gate and human approval.
- Direct commits to `develop` are reserved for an explicitly approved bootstrap or emergency workflow.

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
# backend (requires a running Docker engine for Testcontainers)
cd backend
.\gradlew.bat test

# frontend
cd frontend
npm.cmd ci --legacy-peer-deps
npm.cmd run lint
npm.cmd run build

# merged production compose model
docker compose -f docker-compose.yml -f docker-compose.prod.yml config
```

AI scripts currently have no test suite. At minimum, syntax-check changed Python files and add focused tests for new behavior.

## Known Baseline

The current verified assessment is `docs/assessments/2026-06-10-codebase-audit.md`. Treat older modernization assessments as historical snapshots.
