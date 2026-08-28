# Merchant Limit Adjustment Workflow

Implemented on 2026-08-28.

## Process contract

`merchant-limit-adjustment-v1` is a reviewed identifier-only BPMN resource deployed through
`WorkflowDeploymentService`. Its stable nodes are:

- `limitReviewTask` for claimed merchant/risk review;
- `reviewDecisionGateway` for approve/reject routing;
- `channelSubmitTask` for the first channel command;
- `channelQueryTask` for accepted, processing, or uncertain follow-up queries;
- `effectiveEnd`, `failedEnd`, and `rejectedEnd` for the three terminal outcomes.

Human approval routes to `channelSubmitTask`; it never routes directly to `effectiveEnd`. Only a normalized channel
result of `EFFECTIVE` reaches the effective terminal event. Failed or rejected channel results reach `failedEnd`, while
non-terminal results create a persistent query task.

## Transactional workflow start and mapping

Creating a limit request writes `MERCHANT_LIMIT_ADJUSTMENT_WORKFLOW_START_REQUESTED` into the existing
`biz_outbox_event` table in the same transaction as the request, immutable history, and audit evidence. The payload
contains only tenant, request, merchant, owning-agent, applicant, channel, process-key, business-version, and business-key
references. Applicant reason, amounts, credentials, and channel payloads are excluded.

The shared outbox processor starts the process idempotently with business key
`{tenantId}:MERCHANT_LIMIT_ADJUSTMENT:{requestId}:1`. It then binds the returned process instance to
`biz_limit_adjustment.process_instance_id`, advances the optimistic row version, and appends a `WORKFLOW_STARTED`
history snapshot before publishing the outbox event. A retry returns the existing workflow mapping and repeats the
binding safely.

## Domain-authoritative state mapping

`LimitAdjustmentProcessService` applies claimed reviewer decisions and normalized channel results in the same database
transaction as Flowable task completion:

- approval changes only `approval_status` and leaves channel/effective state unchanged;
- rejection releases the active-request guard without changing the prior effective limit;
- accepted/processing/uncertain channel results retain an active request and route to query;
- rejected/failed results release the active guard and retain the prior effective limit; and
- effective channel confirmation sets `channel_status=SUCCEEDED`, `effective_status=EFFECTIVE`, the confirmed amount
  and effective time, then releases the active guard.

`LimitAdjustmentChannelExecutionService` resolves the current authorized product, builds the project-owned channel DTO,
uses a deterministic business serial, and records only normalized result metadata. Channel tasks require the
`CHANNEL_OPERATIONS` ContiNew role and merchant scope.

## Verification

- the workflow variable policy accepts `requestId` and normalized `channelStatus` only;
- creation tests prove one sanitized workflow event is emitted and duplicate active requests emit no second event;
- MySQL/PostgreSQL integration fixtures deploy the BPMN and cover approve-to-query-to-effective, human rejection, and
  channel failure paths; and
- compilation keeps merchant code dependent only on project-owned workflow/channel APIs, not Flowable implementation
  classes.