# Flowable Engine Runtime Policy

## Process engine scope

- Flowable `7.1.0` process engine only.
- Flowable IDM and Event Registry remain disabled; ContiNew users, roles, tenants, and agent scope stay authoritative.
- The reviewed Liquibase schema uses Flowable's default `ACT_` and `FLW_` prefixes without a runtime-added table prefix or dynamic schema.
- Production `database-schema-update` is fixed to `false` and validated before bean creation.

## History policy

The process engine uses `AUDIT` history. This retains process instances, activity/task history, identifier-only
variables, and business-version references required by phase one without the additional detail volume of `FULL`.
Raw KYC, bank, mobile, credential, binary, and permanent attachment URL values remain prohibited by the workflow
variable allowlist.

## Async executor policy

The executor is enabled with bounded defaults:

| Setting | Value |
|---|---:|
| Core threads | 4 |
| Maximum threads | 16 |
| Queue size | 256 |
| Job retries | 3 |
| Async/timer jobs per acquisition | 8 |
| Acquisition wait | 10 seconds |
| Job/timer lock | 5 minutes |
| Expired-lock reset interval | 60 seconds |

Async history execution remains disabled in phase one; history is written synchronously with the domain/workflow
command while ordinary BPMN async jobs use the persistent Flowable executor.

## Monitoring

Actuator health component `flowableJobs` exposes aggregate executable, timer, suspended, dead-letter, and history-job
counts only. It never returns job payloads, exception stacks, process variables, or business-sensitive values.

Micrometer gauges:

- `flowable.jobs.executable`
- `flowable.jobs.timer`
- `flowable.jobs.suspended`
- `flowable.jobs.dead_letter`
- `flowable.jobs.history`

The health component reports `DEGRADED` when dead-letter jobs exceed the configured threshold and `DOWN` only when
the aggregate job query itself fails. Alert routing and operational repair controls remain tasks 11.5 and 11.6.

## Verification

MySQL 8.4 and PostgreSQL integration tests verify schema mutation is disabled, history is `AUDIT`, the async executor
is active with the configured bounds, Flowable process tables use the reviewed prefix, aggregate job health is `UP`,
and all five gauges are registered.
