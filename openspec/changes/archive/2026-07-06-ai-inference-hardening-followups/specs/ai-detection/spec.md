# ai-detection Specification (delta)

## ADDED Requirements

### Requirement: Supervisor clamps worker capacity to the backend claim bound

The stream supervisor SHALL clamp its configured `MAX_WORKERS` to the backend claim capacity upper bound (64) before submitting claim requests, logging a warning when the configured value exceeds the bound. This prevents a misconfiguration (`MAX_WORKERS > 64`) from causing the deployment to be rejected (HTTP 400) on every claim and silently stall.

#### Scenario: Over-bound MAX_WORKERS is clamped with a warning

- **WHEN** the supervisor starts with `MAX_WORKERS` greater than 64
- **THEN** it uses 64 as the effective worker capacity and logs a warning, so claim requests stay within the backend bound

### Requirement: Inference upload rejects over-limit bodies before parsing

The inference service SHALL reject an upload whose declared `Content-Length` exceeds `AI_MAX_UPLOAD_MB` before the request body is parsed/spooled, returning 413 without buffering the body. The existing chunked/streamed size check SHALL remain as a fallback for requests without a `Content-Length` (chunked transfer).

#### Scenario: Over-limit Content-Length is rejected before body parsing

- **WHEN** a request to `/predict/upload` declares a `Content-Length` exceeding `AI_MAX_UPLOAD_MB`
- **THEN** the service responds 413 before parsing or spooling the multipart body

#### Scenario: Chunked upload without Content-Length still bounded

- **WHEN** a request has no `Content-Length` (chunked transfer) and streams more than `AI_MAX_UPLOAD_MB`
- **THEN** the existing streamed size check aborts it with 413
