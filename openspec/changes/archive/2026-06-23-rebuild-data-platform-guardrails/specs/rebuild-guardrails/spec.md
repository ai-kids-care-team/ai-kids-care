## MODIFIED Requirements

### Requirement: Guardrail backlog content
The guardrail backlog SHALL record every recurring-incident protection removed in the harness
teardown (Change 2) that has not yet been rebuilt. All such guardrails MUST be rebuilt as capability
tests / compile-time guards under their owning capabilities; as of this change all have been rebuilt,
so the backlog is empty.

Rebuilt guardrails and where each is now enforced:
- Shared-container test fixture phone uniqueness (INC-001) — `auth-authorization` capability test
- Spec acceptance coverage map references only existing tests — `auth-authorization` acceptance coverage map
- Neo4j loader must not project S0/PII fields into the graph (INC-003) — `data-platform` loader PII-projection capability test
- MapStruct unmapped target mapping must not be silently dropped (INC-005) — `data-platform` per-mapper `unmappedTargetPolicy=ERROR` compile-time guard
- Schema digest matches migrations (DB drift) — `data-platform` schema-consistency capability test

#### Scenario: Consulting the backlog before release

- **WHEN** a contributor prepares a release
- **THEN** the backlog lists no outstanding un-rebuilt guardrail; each former guardrail is enforced by its owning capability's test suite or compile-time guard

#### Scenario: A rebuilt guardrail leaves the backlog

- **WHEN** a guardrail previously listed in the backlog has been rebuilt as a capability test (e.g., INC-001 phone uniqueness under `auth-authorization`)
- **THEN** it is removed from the backlog and its protection is thereafter enforced by that capability's test suite, not tracked as an open guardrail debt here
