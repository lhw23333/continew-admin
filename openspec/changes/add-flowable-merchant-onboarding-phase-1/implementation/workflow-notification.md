# Workflow Notifications

## Reused ContiNew capability

Workflow notifications use the existing `sys_message` user-message center and WebSocket unread-count signal. No SMS,
email, activation code, Flowable IDM, or second notification UI is introduced.

The message center now supports an internal **前往处理** action. Workflow links use only the local route
`/merchant/workflow` with `tab` and `taskId` query parameters. Opening the link invokes the normal task-detail API, so
tenant, candidate-role, enabled-user, agent-tree, and merchant-scope authorization is re-evaluated server-side. The
path itself grants no access.

## Idempotency queue

`biz_workflow_notification` is the project-owned notification queue and delivery ledger. Each row contains only tenant,
recipient, process/task identifiers, sanitized title/content, internal path, delivery status, and the resulting
ContiNew message ID.

Deterministic keys prevent duplicates:

- assignment/availability: `WORKFLOW_TASK_ASSIGNED:{taskId}:{recipientUserId}`;
- overdue: `WORKFLOW_TASK_OVERDUE:{taskId}:{recipientUserId}`;
- transfer: `WORKFLOW_TRANSFER:{reviewRecordId}:{recipientUserId}`;
- review result: `WORKFLOW_RESULT:{reviewRecordId}:{recipientUserId}`.

The unique `(tenant_id, notification_key)` constraint makes repeated scans and job retries no-ops. Message insertion and
queue transition to `SENT` occur in one database transaction. If delivery fails, both changes roll back and the row
remains `PENDING`; no business decision or Flowable task is repeated.

## Event routing

- Unassigned review tasks notify enabled users in the BPMN candidate groups who also pass merchant scope checks. The
  submitter is excluded from reviewer assignment messages.
- Directly assigned supplement tasks notify the applicant.
- Tasks with a past due date and `escalatedReviewTask` instances generate overdue notifications.
- Transfer creates a direct message for the validated target reviewer.
- Approve, reject, and request-supplement actions notify the submitter/merchant operator with sanitized status only.
- Resubmit does not create a separate result message; the newly available review task generates its own assignment
  notification.

Messages include only application number/ID, task name, normalized status, and the internal task-center path. Opinions,
issue details, legal identifiers, mobile numbers, bank accounts, attachment URLs, KYC JSON, and Flowable variables are
excluded.

## Scheduling

`WorkflowNotificationJob` scans a bounded active-task batch every 30 seconds by default and dispatches a bounded pending
notification batch. The scheduler is disabled in integration tests and can be controlled with:

```yaml
merchant.workflow-notification.scheduler-enabled: true
merchant.workflow-notification.poll-interval-ms: 30000
```

The processor uses explicit tenant IDs while scanning globally and switches into the recipient tenant only for the
existing ContiNew message service call.

## Verification

MySQL 8.4 and PostgreSQL integration tests verify candidate-scope resolution, submitter exclusion, overdue delivery,
transfer/result messages, internal deep links, `PENDING` to `SENT` correlation, and repeated-scan idempotency. The tests
also exercise the PostgreSQL JSON recipient binding used by targeted ContiNew messages.

Frontend type checking, ESLint, related unit tests, and production build verify the message-center navigation and
authorized task-detail deep-link handling.
