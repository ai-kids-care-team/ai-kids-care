## ADDED Requirements

### Requirement: Detection SSE connection keepalive

The realtime detection event stream SHALL emit periodic keepalive frames to every registered SSE connection so that idle connections stay alive across intermediate proxy/NAT timeouts and dead connections are detected and evicted promptly, rather than persisting until the full stream timeout elapses.

#### Scenario: Periodic heartbeat sent to live connections

- **WHEN** the configured heartbeat interval elapses
- **THEN** the service SHALL send a keepalive frame to every currently registered emitter

#### Scenario: Failed heartbeat evicts a dead connection

- **WHEN** sending a heartbeat frame to an emitter throws
- **THEN** that emitter SHALL be removed from the registry immediately, without waiting for the stream timeout

#### Scenario: Client data stream is unaffected by heartbeats

- **WHEN** keepalive frames are emitted as SSE comment frames
- **THEN** the browser `EventSource` SHALL NOT surface them as data events to application code
