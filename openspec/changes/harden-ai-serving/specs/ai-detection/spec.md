# ai-detection Specification (delta)

## ADDED Requirements

### Requirement: Inference endpoint requires bearer authentication

The FastAPI inference service SHALL require a bearer token on its prediction endpoint (`POST /predict/upload`). The token SHALL be injected via environment (`AI_INFERENCE_TOKEN`) and the service SHALL fail fast when it is missing rather than serving predictions unauthenticated. The token value MUST NOT be logged, and it MUST be a distinct environment variable from `AI_SERVICE_TOKEN` (which authenticates the AI→backend direction).

#### Scenario: Prediction without a valid token is rejected

- **WHEN** a caller posts to `/predict/upload` without a bearer token or with an incorrect one
- **THEN** the service responds 401 and does not run inference

#### Scenario: Prediction with the configured token succeeds

- **WHEN** a caller posts to `/predict/upload` with the configured `AI_INFERENCE_TOKEN`
- **THEN** the request proceeds through the existing validation and inference pipeline

### Requirement: Upload size is enforced before full buffering

The inference upload endpoint SHALL reject an over-limit upload before buffering the entire file into memory, using incremental/streamed size enforcement (or an early `Content-Length` check) against `AI_MAX_UPLOAD_MB`. The existing extension whitelist and magic-byte validation SHALL be preserved.

#### Scenario: Over-limit upload is rejected without full buffering

- **WHEN** a caller uploads a file exceeding `AI_MAX_UPLOAD_MB`
- **THEN** the service responds 413 before reading the whole file into memory

#### Scenario: Valid small upload passes validation

- **WHEN** a caller uploads a supported, within-limit video
- **THEN** the size, extension, and magic-byte checks all pass and inference proceeds

### Requirement: Supervisor loads the alert service via package import, not file path

The stream supervisor SHALL load the live alert service (`run_stream_service`) via a normal package import from within `ai_app`, not by loading a file outside the package by path. The ML-heavy import SHALL remain lazy (deferred to the child process / call time), and the existing `scripts/stream_live_alert_service.py` entry path SHALL keep working via a thin re-export shim so deployment entrypoints are unchanged.

#### Scenario: Supervisor import does not pull ML dependencies eagerly

- **WHEN** `ai_app.supervisor` is imported
- **THEN** the ML-heavy alert service module is not imported until a worker child process needs it

#### Scenario: Legacy script entrypoint still resolves

- **WHEN** `scripts/stream_live_alert_service.py` is invoked or imported
- **THEN** it re-exports `run_stream_service` from the in-package module with equivalent behavior
