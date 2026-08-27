# Uncertain Channel Recovery

`UNCERTAIN_RESULT` is audited and then idempotently registered in `biz_channel_recovery` before returning to the
caller. The record contains only tenant/channel/product/config, command and safe query operation, business
type/ID/version/serial, trace ID, retry/lock/error/alert state, and timestamps. It never stores the original command or
response payload.

Onboarding submission maps to onboarding-status query and limit adjustment maps to limit-status query. Commands without
a safe query operation move directly to `REPAIR_REQUIRED`; no recovery path resends an uncertain command.

`ChannelRecoveryProcessor` uses optimistic cross-database claims with stale-lock recovery. A channel-specific
`ChannelRecoveryProbe` queries by the original business serial and is responsible for idempotently applying any returned
event before reporting `RESOLVED`. Pending or retryable results use bounded exponential backoff. Exhausted, permanent,
or unsupported recovery moves to `REPAIR_REQUIRED` and sets a durable pending-alert flag.

`ChannelUncertainRecoveryJob` is the SnailJob executor. When SnailJob is disabled, a Spring scheduled fallback invokes
the same processor. Alert dispatch is separately retryable: ContiNew system messages are sent to enabled agent admin,
merchant reviewer, and risk reviewer users, then the alert row is marked sent.

The recovery API lists only tasks whose onboarding merchant is visible through existing merchant data scope. Manual
requeue requires `workflow:task:review`, reloads the task by tenant, rechecks merchant scope, accepts only
`REPAIR_REQUIRED`, clears retry/error/alert state, and schedules an immediate safe query.

MySQL and PostgreSQL tests prove uncertain registration, query-operation selection, retry count/next retry, resolution
event correlation, unsupported recovery alerting, and manual requeue. Migration tests cover forward/rollback/forward
creation of the recovery table and indexes.
