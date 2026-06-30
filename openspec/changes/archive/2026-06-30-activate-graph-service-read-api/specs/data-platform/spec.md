## ADDED Requirements

### Requirement: Graph query API is reachable and gated by tenant-scoped authorization

The backend relationship-graph read API SHALL be reachable by authenticated tenant staff and SHALL
replace the dormant `@PreAuthorize("denyAll()")` guard on `GraphService` with a method-level
authorization gate. The gate SHALL be a new `AuthorizationAction.GRAPH_READ` evaluated by
`AuthorizationPolicy`, granting access only to a caller with an effective KINDERGARTEN-scoped tenant
identity AND role `TEACHER` or `KINDERGARTEN_ADMIN` (mirroring the detection-event dashboard
audience). The `@PreAuthorize` annotation SHALL be placed on the `GraphService` method (not the
controller), and the controller endpoint SHALL be served under `/api/v1/**` (session + CSRF posture),
NOT under `/api/v1/internal/**` and NOT in any CSRF exemption.

#### Scenario: Authenticated tenant staff reads a child graph

- **WHEN** a user with role `TEACHER` or `KINDERGARTEN_ADMIN` and an active kindergarten identity
  calls `GET /api/v1/graph/children/{childId}` for a child in their own kindergarten
- **THEN** the request is authorized via `GRAPH_READ`, `GraphService.getChildGraph` executes, and the
  child's class/teacher/kindergarten/guardian graph is returned

#### Scenario: denyAll is no longer the guard

- **WHEN** the backend builds after this change
- **THEN** `GraphService.getChildGraph` is annotated with
  `@PreAuthorize("@authorizationPolicy.isAllowed(... GRAPH_READ)")` and no longer carries
  `@PreAuthorize("denyAll()")`

#### Scenario: Wrong role or no tenant identity is denied

- **WHEN** a caller without an effective KINDERGARTEN-scoped tenant identity (e.g. an unauthenticated
  request, or a platform-scoped role such as `SUPERADMIN`/`PLATFORM_IT_ADMIN`, or a `GUARDIAN` under
  the default policy) calls the graph endpoint
- **THEN** `AuthorizationPolicy.isAllowed(GRAPH_READ)` returns false and the request is denied (no
  graph data is returned)

### Requirement: Graph query enforces tenant isolation inside Cypher and hides cross-tenant existence

The active `kindergarten_id` SHALL be obtained from
`EffectiveAuthorizationContextHolder.requireActiveKindergartenId()` (the ThreadLocal tenant context),
NEVER from a URL path/query parameter or request body. The `kindergarten_id` predicate SHALL be
written into the Cypher query itself (anchor `MATCH (ch:Child {child_id: $childId, kindergarten_id:
$kgId})`, with the traversed `Class`/`Teacher`/`Kindergarten`/`Guardian` nodes constrained to the same
`kindergarten_id`); load-then-filter in Java is prohibited. A child that does not exist, or exists only
in another tenant, or is absent for the caller's kindergarten SHALL all yield HTTP 404 (existence
hidden), never 403, 200-with-empty, or 500.

#### Scenario: Cross-tenant child id returns 404

- **WHEN** a `TEACHER`/`KINDERGARTEN_ADMIN` in kindergarten A requests
  `GET /api/v1/graph/children/{childId}` for a `childId` that exists only in kindergarten B
- **THEN** the Cypher anchor `MATCH (ch:Child {child_id: $childId, kindergarten_id: $kgId})` does not
  match, `GraphRepository` raises `jakarta.persistence.EntityNotFoundException`, and the API responds
  HTTP 404 — the same response as for a non-existent child (existence is not disclosed)

#### Scenario: Tenant id comes from context, not the request

- **WHEN** the graph endpoint is invoked
- **THEN** the kindergarten scope is read via `requireActiveKindergartenId()` and bound as the Cypher
  `$kgId` parameter; the client does not (and cannot) supply `kindergartenId` to widen scope

#### Scenario: Tenant predicate is in the query, not post-filtered

- **WHEN** the child graph is resolved
- **THEN** the `kindergarten_id` predicate is present in the executed Cypher (on the `Child` anchor and
  the traversed nodes); the repository does not fetch a broader result and filter it in Java

### Requirement: Graph query responses contain no PII and never join back to PostgreSQL

The graph read API SHALL project only the non-PII node/edge properties already present in the Neo4j
derived graph (which holds no S0/PII fields per INC-003). VO mapping SHALL read exclusively from Neo4j
driver `Node`/relationship values; it MUST NOT join back to PostgreSQL (no JPA/SQL lookup) to enrich
the response, so that no PII column (`rrn_hash`, `rrn_first6`, `rrn_encrypted`, `birth_date`,
`address`, `email`, `phone`, `emergency_contact_*`, `password_hash`, `contact_*`) can re-enter via the
read path. This requirement does not weaken the loader-side INC-003 control; it extends the no-PII
invariant to the query/response path.

#### Scenario: Response carries only graph node properties

- **WHEN** `GET /api/v1/graph/children/{childId}` succeeds
- **THEN** the `ChildGraphVO` contains only graph-projected fields (e.g. child `name`/`childNo`/
  `gender`/`status`, class, teacher, kindergarten, and guardians with edge `relationship`/`isPrimary`/
  `priority`) and no S0/PII field

#### Scenario: VO mapping does not re-read PostgreSQL

- **WHEN** the graph traversal result is mapped to `ChildGraphVO`
- **THEN** the mapping uses only Neo4j driver values returned by `GraphRepository`; it performs no JPA
  repository call or SQL query against PostgreSQL to populate any field

### Requirement: Teacher-centric graph query is reachable and enforces the same tenant isolation

The backend SHALL expose a teacher-centric graph read at `GET /api/v1/graph/teachers/{teacherId}`
returning a `TeacherGraphVO` of the teacher's classes and the children in those classes
(`(Teacher)-[:HAS_CLASS]->(Class)-[:HAS_CHILD]->(Child)`), gated by the same `GRAPH_READ`
authorization action and `@PreAuthorize` on the `GraphService` method (not the controller). The active
`kindergarten_id` SHALL be obtained from `requireActiveKindergartenId()` (ThreadLocal), never from the
URL, and the `kindergarten_id` predicate SHALL be written into the Cypher anchor
`MATCH (t:Teacher {teacher_id: $teacherId, kindergarten_id: $kgId})` with traversed nodes constrained
to the same tenant; load-then-filter is prohibited. A teacher that does not exist or exists only in
another tenant SHALL yield HTTP 404 (existence hidden), and the response SHALL contain no PII and SHALL
NOT join back to PostgreSQL, identical to the child-centric path.

#### Scenario: Authenticated tenant staff reads a teacher graph

- **WHEN** a user with role `TEACHER` or `KINDERGARTEN_ADMIN` and an active kindergarten identity calls
  `GET /api/v1/graph/teachers/{teacherId}` for a teacher in their own kindergarten
- **THEN** the request is authorized via `GRAPH_READ`, `GraphService.getTeacherGraph` executes, and the
  teacher's classes and the children in those classes are returned with no PII fields

#### Scenario: Cross-tenant teacher id returns 404

- **WHEN** a `TEACHER`/`KINDERGARTEN_ADMIN` in kindergarten A requests
  `GET /api/v1/graph/teachers/{teacherId}` for a `teacherId` that exists only in kindergarten B
- **THEN** the Cypher anchor `MATCH (t:Teacher {teacher_id: $teacherId, kindergarten_id: $kgId})` does
  not match, the repository raises `jakarta.persistence.EntityNotFoundException`, and the API responds
  HTTP 404 — the same response as for a non-existent teacher
