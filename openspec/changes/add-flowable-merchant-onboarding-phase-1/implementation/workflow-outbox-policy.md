# Workflow Outbox Delivery Policy

## Scope

Task 8.7 delivers the phase-one `MERCHANT_ONBOARDING_WORKFLOW_START_REQUESTED` command created atomically with final onboarding submission. Human review actions continue to use the proven same-database transaction boundary between domain state, immutable review records, and Flowable task completion.

## Delivery states

- `PENDING`: committed and eligible for first delivery.
- `PROCESSING`: optimistically claimed by one worker; stale locks are recoverable.
- `RETRY`: a retryable engine or deployment failure with a bounded next-attempt time.
- `PUBLISHED`: the idempotent Flowable start and project-owned workflow mapping are durable.
- `REPAIR_REQUIRED`: automatic attempts are exhausted or the event/command is invalid.

The worker starts workflows through the project-owned `WorkflowService`. Its business key makes replay safe when Flowable commits before the outbox row is marked published.

## Retry and repair

Retry uses bounded exponential backoff. Only sanitized categories and fixed operator-safe messages are stored; engine messages, variables, KYC values, channel payloads, and attachment locations are never persisted as errors. Exhausted or non-retryable events move to `REPAIR_REQUIRED` and can be explicitly requeued without deleting or rewriting event history.

## Verification

MySQL 8.4 and PostgreSQL 16 Testcontainers cover successful publication, duplicate replay, a temporarily missing process definition, retry exhaustion, safe error persistence, repair status, and explicit requeue. The full integration suite passes 59 tests with no failures or skips.
