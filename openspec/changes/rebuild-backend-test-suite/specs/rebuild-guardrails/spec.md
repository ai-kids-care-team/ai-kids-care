## MODIFIED Requirements

### Requirement: Guardrail backlog content
The backlog SHALL record at least the following removed protections and the capability that owns
each one's rebuild:
- Neo4j loader must not project S0/PII fields into the graph (INC-003) — owner `data-platform`
- MapStruct unmapped target mapping must not be silently dropped (INC-005) — owner `data-platform`
- Schema digest matches migrations (DB drift) — owner `data-platform`

Protections that have been rebuilt as capability tests SHALL be removed from this backlog and
are thereafter enforced by their owning capability's test suite. The following were rebuilt under
the `auth-authorization` capability and are no longer carried here:
- Shared-container test fixture phone uniqueness (INC-001) — rebuilt as an `auth-authorization` capability test
- Spec acceptance coverage map references only existing tests — rebuilt as an `auth-authorization` acceptance coverage map

#### Scenario: Consulting the backlog before release

- **WHEN** a contributor prepares a release touching a capability listed above
- **THEN** the backlog identifies the guardrail to (re-)build for that capability before release

#### Scenario: A rebuilt guardrail leaves the backlog

- **WHEN** a guardrail previously listed in the backlog has been rebuilt as a capability test (e.g., INC-001 phone uniqueness under `auth-authorization`)
- **THEN** it is removed from the backlog and its protection is thereafter enforced by that capability's test suite, not tracked as an open guardrail debt here
