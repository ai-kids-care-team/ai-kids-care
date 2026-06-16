# Backend test conventions (fixture pitfalls)

Hard-won conventions for writing backend tests against this schema. Pair with the
generated [`schema-digest.md`](schema-digest.md) (every NOT NULL / UNIQUE / FK / enum)
and run them locally with `bash scripts/test-backend.sh '<pattern>'` before pushing.
Each item below is a real failure that reached CI at least once — treat them as a checklist.

## 1. Globally-unique `users` columns across the SHARED Testcontainer

`BaseIntegrationTest` starts ONE Postgres, shared across every integration test class
(Spring context caching). `users` has UNIQUE on **`login_id`, `email`, AND `phone`**
(see digest). `upsertUser` uses `ON CONFLICT (login_id)`, which does NOT catch an
`email`/`phone` collision with a DIFFERENT login_id from ANOTHER test class → the insert
fails with `DuplicateKeyException`.

**Convention: each integration test class owns a unique `login_id` prefix AND a unique
phone prefix.** Known assignments (grep `010-0` in `src/test` before picking a new one):

| test class | login prefix | phone prefix |
| --- | --- | --- |
| GuardianChildAuthorizationIntegrationTest | `gc-*` | `010-0700-*` |
| TeacherChildAuthorizationIntegrationTest | `tc-*` | `010-0800-*` |
| NotificationReadAuthorizationIntegrationTest | `nr-*` | `010-0905-*` |

(This exact collision — NotificationRead reusing `010-0800-*` — broke 6 TeacherChild tests.)

## 2. Composite tenant FKs — cross-tenant test data is not free

Almost every child table has a COMPOSITE FK `(kindergarten_id, <entity>_id) → parent(kindergarten_id, <entity>_id)`
(see digest "Foreign keys"). E.g. `notifications (kindergarten_id, event_id) → detection_events`.
You therefore CANNOT insert a row in kindergarten B that references a parent in kindergarten A.

- The seed `detection_events` are ALL in kindergarten 1, so a foreign-kindergarten
  `notification`/`event_review`/`evidence_file` cannot be created cheaply (you'd have to
  build a whole foreign camera→session→event chain first).
- For cross-tenant 404 coverage, prefer the same-tenant-wrong-owner path (e.g. another
  recipient's notification) or an existing seed row in another kindergarten; the
  `kindergarten.id = :kgId` filter is the same SQL clause either way. (This is why
  NotificationRead's cross-tenant insert blew up and was dropped.)

## 3. `ON CONFLICT (cols)` needs a matching UNIQUE index

PostgreSQL infers the arbiter index by the column SET (order-independent), so
`ON CONFLICT (user_id, kindergarten_id)` matches the digest's
`user_kindergarten_memberships (kindergarten_id, user_id)`. If no unique index covers
exactly those columns, the statement errors even when no row conflicts. Check the digest.

## 4. KINDERGARTEN_ADMIN needs only role + membership (no profile)

`EffectiveAuthorizationContextService.resolve` requires: ACTIVE user, exactly ONE ACTIVE
`user_role_assignment`, and (KINDERGARTEN scope) exactly ONE ACTIVE membership for that
kindergarten. It does NOT read a teacher/guardian profile. So an admin fixture for a
coarse-gate 403/role test needs only role + membership; a DIRECTOR-level `teachers` row is
only needed when the test exercises `KindergartenAdminPolicy` (approval level checks).

## 5. Relaxing NOT NULL needs BOTH the migration AND the entity

`ddl-auto=validate` does NOT check nullability. To actually allow null (e.g. OQ-DATA-3
notifications pending columns) you must change the Flyway migration (`DROP NOT NULL`) AND
the entity (`@NotNull` / `nullable=false`) — otherwise the app still rejects null before
insert. See V3 + `Notification`.

## 6. Adding a NOT NULL association → ignore it in closed write mappers

Mapping a new NOT NULL `@ManyToOne` on an entity makes MapStruct see an unmapped target in
the closed `toEntity`/`updateEntity` methods (default policy is WARN, but be explicit):
add `@Mapping(target = "<field>", ignore = true)`. The read `toVO` is unaffected. See
`NotificationMapper` + the `Notification.kindergarten` field.

## 7. Enum binds need an explicit cast

Bind parameters are typed `text`, so casting is required: `?::status_enum`,
`'PUSH'::notification_channel_enum`, `'DIRECTOR'::level_enum`. Use the exact labels from
the digest's "Enum types". (A plain string literal in a `VALUES (...)` coerces; a `?` bind
does not.)

## 8. FK-safe teardown on the shared container

The container persists across tests in a class, so each test must clean up its own rows,
children first: delete assignment/relationship/`audit_logs` (by `resource_id`) rows before
the parent (`children`/`notifications`/`teacher` etc.). Mirror the `deleteChild*` /
`@AfterEach` helpers in the existing security integration tests.

## Workflow

1. Writing fixtures or JPQL? Open [`schema-digest.md`](schema-digest.md) instead of
   grepping the DDL. Regenerate it after a migration: `bash scripts/schema-digest.sh`.
2. Run the affected class locally before pushing: `bash scripts/test-backend.sh '*YourTest*'`.
3. Hit a new fixture failure mode? Add it here (the "recurring issue → add a control"
   harness rule).
