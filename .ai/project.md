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
