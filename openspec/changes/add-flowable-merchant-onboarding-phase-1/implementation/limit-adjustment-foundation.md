# Limit Adjustment Foundation

## Aggregate and state

`LimitAdjustment` is the merchant-owned aggregate for one monthly inbound-limit request. It retains request number,
tenant, merchant and owning agent, channel and inbound platform, currency, original/requested/normalized/effective
amounts, reason, applicant/time, eligibility and connection configuration versions, process reference, independent
approval/channel/effective states, result details, and optimistic business version.

Creation starts at `PENDING / NOT_SUBMITTED / NOT_EFFECTIVE`. It deliberately leaves `processInstanceId` null until task
10.3 starts and maps the reviewed Flowable process. An approval alone cannot populate the effective amount or state.

## Eligibility and baseline

The service accepts requested and already server-normalized values; task 10.2 owns configurable minimum, maximum,
precision, thousand-rounding, and preview confirmation. It never accepts the original effective limit or eligibility
versions from a caller.

Eligibility is resolved from authoritative current state:

- the merchant is accessible and `ENABLED`;
- the latest application for the selected channel is successfully onboarded;
- its exact pricing product has an effective enabled channel-product version supporting the merchant type; and
- an effective connection configuration exposes the limit-adjustment operation.

The original limit is the latest `EFFECTIVE` request for the same tenant/merchant/channel/platform/currency dimension.
The first adjustment uses the existing channel contract's zero baseline because no earlier effective adjustment exists.
Task 10.4 will revalidate this baseline and the saved configuration versions before final approval/channel submission.

## Uniqueness and immutable evidence

`active_request_guard=ACTIVE` and a database unique index enforce one active request per
tenant/merchant/channel/platform in both MySQL and PostgreSQL. A repeated submission returns the active request with
`created=false`; a database uniqueness race is resolved to the winning request. Request numbers are tenant-unique.

Database triggers prevent physical request deletion and mutation of identity, original/requested/normalized values,
reason, applicant/time, and saved eligibility/configuration versions while allowing later state-machine columns to
advance. Every domain action also appends a full state snapshot to `biz_limit_adjustment_history`; that table rejects all
updates and deletes. The MySQL compound trigger is kept in a dedicated `splitStatements:false` changeset, and migration
round-trip tests replay it as one SQL statement.

## Scope and audit

History access first confirms the request belongs to the authorized tenant and merchant. Creation appends a sanitized
`LIMIT_ADJUSTMENT_CREATE` security audit with amount and version references but omits the free-form applicant reason.
Later withdraw, review, channel, and effective actions will reuse the history/audit boundary in tasks 10.3-10.6.

## Verification

- focused service tests cover server-owned baseline, eligibility failure, active-request reuse, history scope, and audit;
- MySQL 8.4 and PostgreSQL 16 scenarios cover the real eligibility chain, persistence, unique guard, immutable request
  evidence, append-only history, ineligible rejection, and durable audit; and
- both database migration round trips prove create, rollback, and recreate behavior for the new history and triggers.
