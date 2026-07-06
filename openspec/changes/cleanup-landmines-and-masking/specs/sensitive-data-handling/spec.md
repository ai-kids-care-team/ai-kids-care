# sensitive-data-handling Specification (delta)

## ADDED Requirements

### Requirement: Stream URL credential masking handles embedded userinfo separators

The camera stream URL credential masking helper SHALL mask the full userinfo component of a URL even when the userinfo contains additional unescaped `@` characters, leaving no plaintext credential fragment in logs or error messages. Masking SHALL continue to use a regex substitution (not a parse/round-trip) and SHALL NOT alter the URL path or query.

#### Scenario: Userinfo with an embedded literal @ is fully masked

- **WHEN** a stream URL such as `rtsp://user:pa@ss@host/path` is masked
- **THEN** the entire `user:pa@ss` userinfo is replaced and no plaintext credential fragment (e.g. `ss`) remains before the host

#### Scenario: URL without userinfo is unchanged

- **WHEN** a URL with no credentials is masked
- **THEN** it is returned unchanged, path and query preserved

### Requirement: No unwired tenant-unsafe service methods remain as authorization landmines

The codebase SHALL NOT retain service methods that lack both `@PreAuthorize` and a tenant predicate while having zero callers, since such methods are latent cross-tenant authorization landmines for future contributors. When list/get/create operations without authorization and tenant scoping are unreachable (no controller mapping, no caller), they SHALL be removed rather than left in place.

#### Scenario: Unwired unauthenticated list/get methods are removed

- **WHEN** a service exposes a list/get/create method with neither `@PreAuthorize` nor a tenant predicate and it has no controller mapping and no caller
- **THEN** the method is removed, and the authorized, tenant-scoped counterpart used by the controller remains
