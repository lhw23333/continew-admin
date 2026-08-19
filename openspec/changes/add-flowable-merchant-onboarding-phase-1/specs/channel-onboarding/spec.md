## ADDED Requirements

### Requirement: Channel integration uses a stable adapter contract
**Trace ID:** CHN-001

The system SHALL define a channel adapter contract for onboarding submission, status query, signing link, bank-account information, and limit adjustment, while isolating channel DTOs from domain models.

#### Scenario: Submit using configured adapter
- **GIVEN** a valid application references an enabled channel code
- **WHEN** domain submission is requested
- **THEN** the matching adapter builds and sends the channel request and returns a normalized submission result

### Requirement: Channel request is built from authorized domain data
**Trace ID:** CHN-002

The channel service SHALL load the exact submitted KYC version by application ID, decrypt only fields required by the channel, construct the request in controlled memory, and SHALL NOT persist the raw request in Flowable variables, generic caches, or ordinary access logs.

#### Scenario: Build request for submitted version
- **GIVEN** application 100 references KYC version 3
- **WHEN** the adapter builds the channel request
- **THEN** it reads version 3 through an authorized domain service and never substitutes the merchant's later draft version

### Requirement: Outbound requests are authenticated and traceable
**Trace ID:** CHN-003

The system MUST apply channel-configured signing, encryption, timestamp, nonce, certificate/key version, business serial number, trace ID, and sanitized request audit to every outbound command.

#### Scenario: Missing signing key
- **GIVEN** the required channel signing key is unavailable or expired
- **WHEN** submission is attempted
- **THEN** the system does not send the request and raises an operational alert without logging secret material

### Requirement: Callback authenticity is verified before state change
**Trace ID:** CHN-004

The system MUST verify callback signature, certificate/key version, timestamp tolerance, nonce or replay protection, channel identity, and required fields before recording a channel event or changing normalized state.

#### Scenario: Invalid callback signature
- **GIVEN** a callback signature does not verify
- **WHEN** the callback endpoint receives it
- **THEN** no business state changes and a sanitized security event is recorded

### Requirement: Channel events are idempotent
**Trace ID:** CHN-005

The system SHALL deduplicate channel callbacks and polling results by channel event ID or deterministic idempotency key and MUST apply a channel event at most once.

#### Scenario: Duplicate successful callback
- **GIVEN** a successful callback was already processed
- **WHEN** the identical callback is received again
- **THEN** the endpoint returns the configured acknowledgement without duplicating state history or workflow actions

### Requirement: Normalized channel state cannot regress
**Trace ID:** FR-ONB-017, CHN-006

The system SHALL preserve raw channel status and map it through a versioned mapping to independent normalized sub-states for reporting, signing, card binding, reserve-account opening, and final outcome.

#### Scenario: Older callback follows newer state
- **GIVEN** card binding is already successful
- **WHEN** an older report-success callback arrives
- **THEN** the event is stored for traceability but the card-binding and final normalized states do not regress

### Requirement: Retry policy distinguishes safe and unsafe operations
**Trace ID:** CHN-007

The system SHALL configure timeout, circuit breaker, bulkhead, and retry per channel operation; non-idempotent commands MUST NOT be blindly retried after an uncertain result.

#### Scenario: Submission times out after send
- **GIVEN** a submission request times out after transmission
- **WHEN** recovery begins
- **THEN** the system queries by business serial number or waits for callback before deciding whether to resend

### Requirement: Polling and manual recovery are observable
**Trace ID:** CHN-008

The system SHALL use scheduled jobs for supported status polling and retryable recovery, and SHALL expose failed/uncertain events, retry count, next retry, last error category, and authorized manual recovery actions.

#### Scenario: Polling eventually resolves status
- **GIVEN** a channel submission is in an uncertain state and supports query
- **WHEN** a scheduled poll returns a final result
- **THEN** the system idempotently records the result and resumes the appropriate domain/workflow transition

### Requirement: Channel configuration is versioned and secret-safe
**Trace ID:** CHN-009

The system SHALL version channel endpoint, timeout, product, status-map, certificate, and key references; secrets MUST be externalized and encrypted, and prior business events SHALL remain traceable to the configuration version used.

#### Scenario: Rotate channel certificate
- **GIVEN** a new certificate becomes effective
- **WHEN** requests are sent after the effective time
- **THEN** the adapter uses the new key reference while still accepting configured overlap for valid callbacks

### Requirement: Channel attachment transfer uses private objects
**Trace ID:** CHN-010

The system SHALL transfer only authorized KYC attachment objects required by the channel and MUST use controlled streams or short-lived channel-specific URLs without exposing permanent storage locations.

#### Scenario: Channel requests evidence file
- **GIVEN** the application version references a required private attachment
- **WHEN** the adapter submits the evidence
- **THEN** access is limited to the channel operation and the transfer is audited by object ID and hash
