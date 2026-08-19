## ADDED Requirements

### Requirement: Eligible merchant can create a limit request
**Trace ID:** FR-LIM-001

The system SHALL allow an authorized user to create a monthly inbound-limit request for an eligible merchant, channel, and inbound platform with requested limit and reason.

#### Scenario: Create valid request
- **GIVEN** the merchant and channel are active, successfully onboarded, and support adjustment
- **WHEN** the operator submits valid channel, platform, limit, and reason
- **THEN** the system creates one versioned request and starts the configured Flowable approval process

#### Scenario: Ineligible merchant
- **GIVEN** the merchant is disabled or the channel onboarding is incomplete
- **WHEN** adjustment is submitted
- **THEN** the service rejects it without starting a workflow

### Requirement: Requested amount follows configured rounding
**Trace ID:** FR-LIM-002

The system SHALL validate requested amount against configured minimum, maximum, currency precision, and thousand-unit upward-rounding rules and SHALL display both entered and normalized values before confirmation.

#### Scenario: Normalize non-thousand amount
- **GIVEN** the entered amount is valid but not a whole thousand
- **WHEN** the request is previewed
- **THEN** the configured upward-rounded amount is shown and used only after explicit confirmation

### Requirement: Processing requests are unique
**Trace ID:** FR-LIM-003

The system MUST prevent multiple active adjustment requests for the same tenant, merchant, channel, and inbound platform unless business policy explicitly permits them.

#### Scenario: Duplicate active request
- **GIVEN** an active request already exists for the same dimensions
- **WHEN** another request is submitted
- **THEN** the service returns the active request reference and does not start a second process

### Requirement: Original and target limits are immutable evidence
**Trace ID:** FR-LIM-004

Each adjustment request SHALL record original effective limit, requested limit, normalized limit, reason, applicant, application time, channel/configuration version, and associated process instance.

#### Scenario: Effective limit changes during approval
- **GIVEN** the effective limit changed after the request was submitted
- **WHEN** approval reaches final decision
- **THEN** the service detects the version conflict and applies configured revalidation rather than silently overwriting the newer limit

### Requirement: Approved limit has explicit effective state
**Trace ID:** FR-LIM-005

An approved workflow SHALL NOT by itself make a limit effective; the domain service SHALL update effective state only after required channel confirmation and shall preserve prior limit history.

#### Scenario: Approval succeeds but channel rejects
- **GIVEN** human approval is complete
- **WHEN** the channel rejects the adjustment
- **THEN** the original limit remains effective and the request records channel rejection separately from approval result

### Requirement: Adjustment history is complete
**Trace ID:** FR-LIM-006

The system SHALL expose request number, merchant, channel, platform, original and requested limits, status, applicant, process history, application time, approval time, effective time, opinion, and channel result to authorized users.

#### Scenario: View adjustment history
- **GIVEN** the user has access to the merchant
- **WHEN** adjustment history is requested
- **THEN** all authorized immutable request versions are returned without leaking unrelated merchant data

### Requirement: Adjustment actions are audited
**Trace ID:** AUD-LIM-001

The system MUST audit create, withdraw, approve, reject, channel-submit, channel-result, and effective-limit actions with sanitized values and version references.

#### Scenario: Reject adjustment
- **GIVEN** an authorized reviewer rejects a request with an opinion
- **WHEN** the Flowable task completes
- **THEN** both workflow history and domain approval audit reference the same request and business version
