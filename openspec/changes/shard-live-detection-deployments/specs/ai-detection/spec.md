## ADDED Requirements

### Requirement: Claim/lease based stream distribution across GPU deployments

The backend SHALL distribute active streams across multiple AI deployments (each bound to a GPU) as
a balanced work pool via a claim/lease protocol, so that no deployment consumes the full set of
streams and load is balanced by each deployment's capacity rather than by tenant. The backend SHALL
expose `POST /api/v1/internal/streams/claim` (`ROLE_AI_SERVICE`) taking `{deploymentId, capacity,
running[]}` and returning the streams that deployment SHALL run as `{assigned: [{streamId, modelId,
kindergartenId}]}` (no decrypted credentials). On each claim the backend SHALL: (1) renew the lease
for each still-active stream in `running` that the caller currently holds; (2) if the caller has
spare capacity, atomically claim currently-unleased active streams up to that spare capacity; and
(3) return the union as `assigned`. Leases SHALL be stored with a time-to-live (TTL) that expires if
a deployment stops claiming, so that a failed deployment's streams return to the unclaimed pool and
are picked up by other deployments. Claiming SHALL be atomic so two deployments never hold the same
stream's lease simultaneously. Tenant attribution remains server-side (the backend derives
`kindergarten_id` from `streamId` on ingest); an AI deployment MAY run streams from multiple
kindergartens.

#### Scenario: Deployment claims up to its capacity

- **WHEN** a deployment with capacity K and no current leases calls the claim endpoint while more
  than K active streams are unclaimed
- **THEN** the backend leases exactly K unclaimed active streams to that deployment and returns them
  as `assigned`, leaving the remaining streams claimable by other deployments

#### Scenario: Running streams are renewed, not double-assigned

- **WHEN** a deployment calls the claim endpoint listing streams it is already running in `running[]`
- **THEN** the backend renews those leases (extends their TTL) and does not assign them to any other
  deployment, and fills only the caller's remaining spare capacity with new streams

#### Scenario: Capacity bounds the assigned set including renewals

- **WHEN** a deployment claims with a `capacity` smaller than the number of streams it lists in
  `running` (e.g. its `MAX_WORKERS` was lowered), including `capacity = 0`
- **THEN** the backend renews at most `capacity` of those leases and returns at most `capacity`
  streams in `assigned` (an empty `assigned` when `capacity = 0`); the excess leases are not renewed
  and expire by TTL, so `assigned.size()` never exceeds `capacity`

#### Scenario: A failed deployment's streams are reassigned

- **WHEN** a deployment stops calling the claim endpoint (crash or network partition) and its stream
  leases expire
- **THEN** those streams return to the unclaimed pool and, on their next claim, are leased to other
  deployments that have spare capacity

#### Scenario: A newly added camera is picked up automatically

- **WHEN** a new stream becomes active (`enabled = true`) while some deployment has spare capacity
- **THEN** that stream is unclaimed and is leased to a deployment with spare capacity on a subsequent
  claim, without any configuration change

#### Scenario: Concurrent claims never double-lease a stream

- **WHEN** two deployments attempt to claim the same unleased stream concurrently
- **THEN** at most one succeeds in acquiring the lease and the other does not receive that stream in
  its `assigned` set

#### Scenario: Transient double-run is deduplicated, not corrupted

- **WHEN** a lease is reassigned while the previous holder is slow (not dead) and both briefly run
  the same stream
- **THEN** duplicate detection events carry the same `dedupKey` and the backend deduplicates on
  `(kindergarten_id, dedup_key)`, so no duplicate `detection_events` row is written

#### Scenario: Cross-lease credential request is hidden

- **WHEN** a deployment requests `GET /api/v1/internal/streams/{id}/credentials` for a stream it does
  not currently hold a lease for
- **THEN** the backend returns 404 and does not disclose the stream's existence or credentials

### Requirement: Bounded live-detection worker pool with explicit over-capacity signalling

The live-detection supervisor SHALL manage its detection workers as a bounded, self-healing pool
whose ceiling is the configured `MAX_WORKERS`, which it also reports as its `capacity` when claiming
streams. The pool SHALL be reconciled against the streams currently assigned to the deployment by the
claim protocol: a worker is started for each newly assigned stream, stopped for each stream the
deployment no longer holds (lease lost or camera removed), and restarted (with backoff) when it exits
terminally, without disturbing unaffected workers. The supervisor SHALL NOT silently drop streams: if
the assigned set and the locally running set diverge, or a claim call fails, it SHALL emit an
explicit warning-level log rather than failing silently, and SHALL NOT crash on a failed claim.

#### Scenario: Supervisor reconciles workers to the assigned set

- **WHEN** the claim endpoint returns an assigned set that differs from the workers currently running
- **THEN** the supervisor starts a worker for each newly assigned stream and stops the worker for
  each stream no longer assigned, leaving unaffected workers running

#### Scenario: Failed claim does not crash the supervisor

- **WHEN** a claim call fails (backend unreachable or errors)
- **THEN** the supervisor logs the failure, keeps its currently running workers, and retries on the
  next cycle without terminating

### Requirement: GPU-enabled deployment must not break GPU-less environments

GPU device reservation for the AI containers SHALL live only in an opt-in Compose overlay
(`ai/docker-compose.gpu.yml`); the base `ai/docker-compose.yml` SHALL declare no hard GPU
requirement so that `docker compose -f ai/docker-compose.yml up` and the CI `Compose config` check
succeed on hosts without a GPU or NVIDIA runtime. The live-detection supervisor service SHALL be
placed behind a Compose `profile` so a default `up` on a GPU-less host starts only the FastAPI
inference service and does not launch the supervisor. A GPU host SHALL enable detection by composing
the base file with the GPU overlay and the supervisor profile.

#### Scenario: GPU-less host can still compose up

- **WHEN** `docker compose -f ai/docker-compose.yml config` (or a default `up`) runs on a host with
  no GPU / no NVIDIA container runtime
- **THEN** it succeeds, and no service declares a hard nvidia device reservation

#### Scenario: GPU host enables detection via overlay and profile

- **WHEN** a GPU host runs `docker compose -f ai/docker-compose.yml -f ai/docker-compose.gpu.yml`
  with the supervisor profile enabled
- **THEN** the inference service and the live-detection supervisor both start with GPU device access

### Requirement: V1 detection target is the assault label

For V1, live detection SHALL target the single event label `assault`; other event types in the
label-mapping table are out of scope for V1 and are deferred to a later version. This closes the
interim ambiguity in the AI detection state machine (which already watches only `assault`).

#### Scenario: Only assault triggers V1 alarms

- **WHEN** the live-detection state machine evaluates inference output in V1
- **THEN** it raises alarms only for the `assault` target label and does not raise alarms for other
  labels in the mapping table
