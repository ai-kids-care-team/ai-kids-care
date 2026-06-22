# rebuild-guardrails Specification

## Purpose
记录 Change 2 拆除的 recurring-incident 护栏（INC-001/003/005、schema-digest 漂移、spec 验收覆盖），作为各能力以 TDD 重建前的耐久 backlog。
## Requirements
### Requirement: Recurring-incident guardrails are rebuilt as capability tests
Protections removed in the harness teardown (Change 2) SHALL be rebuilt as tests via TDD when
their owning capability is (re-)specified in OpenSpec, rather than as a separate harness layer.
The repository SHALL keep a durable backlog of these guardrails until each is rebuilt.

#### Scenario: Re-specifying a capability that had a guardrail
- **WHEN** a capability whose behavior was previously protected by a harness guardrail is
  (re-)specified as an OpenSpec change
- **THEN** the equivalent protection is authored as a capability test via TDD as part of that change

### Requirement: Guardrail backlog content
The backlog SHALL record at least the following removed protections and the capability that owns
each one's rebuild:
- Shared-container test fixture phone uniqueness (INC-001) — owner `auth-authorization`
- Neo4j loader must not project S0/PII fields into the graph (INC-003) — owner `data-platform`
- MapStruct unmapped target mapping must not be silently dropped (INC-005) — owner `data-platform`
- Schema digest matches migrations (DB drift) — owner `data-platform`
- Spec acceptance coverage map references only existing tests — owner `auth-authorization`

#### Scenario: Consulting the backlog before release
- **WHEN** a contributor prepares a release touching a capability listed above
- **THEN** the backlog identifies the guardrail to (re-)build for that capability before release

