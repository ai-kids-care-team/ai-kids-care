## ADDED Requirements

### Requirement: Child relationship graph is reachable from role-appropriate navigation

The child relationship graph UI SHALL be reachable from the primary navigation for `KINDERGARTEN_ADMIN` and `TEACHER` roles, and its entry SHALL accept a child chosen from an existing roster/list (carrying the resolved `childId`) instead of requiring a manually typed raw numeric identifier. `GUARDIAN` SHALL NOT be shown a graph navigation entry. Tenant isolation and the no-PII / no-PostgreSQL-join guarantees of the underlying graph query remain unchanged and continue to be enforced server-side inside Cypher.

#### Scenario: Director and teacher see a graph entry

- **WHEN** a `KINDERGARTEN_ADMIN` or `TEACHER` views the primary navigation
- **THEN** a graph entry (e.g. "관계 그래프") is present and links to the graph page

#### Scenario: Guardian has no graph entry

- **WHEN** a `GUARDIAN` views the primary navigation
- **THEN** no graph navigation entry is shown

#### Scenario: Graph is entered by selecting a child, not by typing a raw id

- **WHEN** an authorized user opens the graph by selecting a child from an existing roster/list
- **THEN** the graph loads for that child's resolved `childId` without the user typing a raw numeric primary key

#### Scenario: Frontend never sends kindergartenId

- **WHEN** the graph page issues its query
- **THEN** the request carries no `kindergartenId` parameter and tenant scoping is resolved server-side from the session context
