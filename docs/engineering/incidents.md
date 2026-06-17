# Incident ledger (recurring mistakes → controls)

Operationalizes the harness rule **"recurring issue → add a control"** (see
[`harness.md`](harness.md)). Each row is a real mistake that reached CI or review at least once,
the *class* of mistake, and the **control** that now prevents it — tagged **computational**
(deterministic, runs in CI/local) or **inferential** (a doc/convention a human or agent must read).
The runnable computational controls are this repo's deterministic "eval set": rerun them and the
incident class cannot silently recur.

**When a mistake reaches CI/review, add a row here and add (or link) its control** — prefer a
computational one. This is the feedback loop that grows the harness.

| # | Date | Class of mistake | What happened | Control | Kind | Runnable eval |
| --- | --- | --- | --- | --- | --- | --- |
| INC-001 | 2026-06-16 | Test fixture — shared-container UNIQUE collision | `NotificationReadAuthorizationIntegrationTest` reused TeacherChild's full phone `010-0800-0001`; `users.phone` is UNIQUE on the shared Testcontainer → `DuplicateKeyException` broke 6 TeacherChild tests | `TestFixturePhoneUniquenessTest` (build fails on any full-phone literal shared by two classes) + [`test-conventions.md`](test-conventions.md) §1 | computational | ✅ `*TestFixturePhoneUniqueness*` |
| INC-002 | 2026-06-16 | Test fixture — composite tenant FK | A cross-tenant `notifications` insert hit the composite FK `(kindergarten_id, event_id) → detection_events` (seed events only in kg1) → insert failed | [`test-conventions.md`](test-conventions.md) §2 — prefer same-tenant-wrong-owner for 404 coverage | inferential | — |
| INC-003 | 2026-06-16 | Security — S0/PII projection | Neo4j loaders projected `password_hash` / `email` / `phone` / `rrn*` / `address` into graph nodes, violating SPEC-0001 §365 | loader scrub + idempotent `no000_scrub_sensitive.py`; **`LoaderSensitiveProjectionGuardTest`** — build fails if a loader projects a forbidden field as a node property | computational | ✅ `*LoaderSensitiveProjection*` |
| INC-004 | 2026-06-16 | Schema — `validate` ignores nullability | Relaxing `notifications` NOT NULL columns needed BOTH a Flyway migration AND the entity; `ddl-auto=validate` does not catch nullability | V3 migration + `Notification` entity + [`test-conventions.md`](test-conventions.md) §5 | inferential | — |
| INC-005 | 2026-06-16 | Mapper — unmapped NOT NULL association | A new NOT NULL `@ManyToOne` (`Notification.kindergarten`) left an unmapped target in the closed write mappers | `@Mapping(target=..., ignore=true)` + **MapStruct `unmappedTargetPolicy = ERROR`** (`backend/build.gradle`) — a forgotten mapping now fails compile | computational | ✅ compile (`test-backend.sh --compile`) |
| INC-006 | 2026-06-16 | Build — CRLF shebang under Docker | `*.sh` checked out CRLF on Windows (autocrlf) → an image built from the Windows tree failed `exec ./run_all.sh` (`\r` in shebang) | `.gitattributes` `*.sh text eol=lf` | computational | ✅ gitattributes (checkout normalization) |
| INC-007 | 2026-06-16 | Tooling — Testcontainers bind under DooD | `withFileSystemBind` for `db/initdb` did not resolve when Gradle runs in a container against the host daemon | `BaseIntegrationTest` → `withCopyFileToContainer` + `scripts/test-backend.sh` | computational | ✅ the local test loop + CI |
| INC-008 | 2026-06-17 | Harness — inert agent hooks | `node`/`bash` not reliably on PATH (node bundled in the app; bash = WSL) → `node`/`bash` Claude Code hooks were silently inert on this box | bare-`git` Stop hook + CI checks; [`.claude/hooks/README.md`](../../.claude/hooks/README.md) | inferential | — |
| INC-009 | 2026-06-17 | Harness — digest misses new migrations | `schema-digest.sh` applied a hardcoded `V2,V3` list → a future `V4` would be silently ignored | auto-discover all `V*__*.sql` (skip the V1 baseline) + `schema-digest-drift.yml` (CI fails on drift) | computational | ✅ `Schema Digest Drift` |

## Deferred (honest scope)

A full **agent-behaviour eval runner** — scenario prompts replayed against an agent with an
LLM-as-judge verdict — is a **deliberate non-goal**
([ADR-0023](../decisions/adr/ADR-0023-harness-behaviour-eval-scope.md)), not an oversight. The
pragmatic core is here: this ledger plus the deterministic guardrail tests it links — themselves
proven to fire on planted violations (`HarnessGuardsSelfTest`) — which give binary pass/fail
coverage of the failure *classes* we have actually hit.

Both former "candidate" computational controls are now implemented: the §365 loader projection
guard (INC-003) and MapStruct `unmappedTargetPolicy = ERROR` (INC-005).
