# Workflow Task Center

## Backend boundary

The task center is exposed under `/workflow/tasks` and always derives tenant and user IDs from the authenticated
ContiNew context. Clients cannot supply candidate groups, agent scope, merchant scope, or assignee identities.

Endpoints:

- `GET /todo`, `GET /claimed`, and `GET /done` return stable paged task rows;
- `GET /{taskId}` returns an authorized task detail;
- `GET /processes/{processInstanceId}/history` returns sanitized Flowable activity/task history;
- `POST /{taskId}/claim` and `/unclaim` manage ordinary human review claims;
- existing `/actions` and `/transfer` endpoints remain the only mutation paths for review decisions and transfer.

Applicant supplement tasks are directly assigned by BPMN and cannot be unclaimed because doing so would leave a task
without a candidate identity. All direct API calls still pass Flowable tenant/candidate checks plus merchant ownership
authorization.

## Sanitized projection

`WorkflowTaskCenterService` combines the engine-neutral task DTO with merchant-owned read models. The response contains
only application/merchant identifiers, business version, merchant number and names, masked legal identifier, masked
contact mobile, channel/product, application status, KYC version references, category-only supplement differences, and
sanitized immutable review records.

It never returns Flowable variables, ciphertext, complete identity numbers, complete mobile numbers, bank accounts,
passwords, attachment object keys, permanent URLs, or raw KYC JSON. Completed-task detail uses sanitized Flowable
history without reopening the task for mutation.

## Menu and permissions

The dynamic route is `merchant/workflow/index` under `/merchant/workflow`. Permissions are split into:

- `workflow:task:list`
- `workflow:task:get`
- `workflow:task:claim`
- `workflow:task:review`
- `workflow:task:transfer`
- `workflow:task:history`

Merchant operators and merchant reviewers receive task-center access required for their assigned tasks. A system
`RISK_REVIEWER` role is added with transfer and overdue-review access. Server-side business scope remains authoritative
even when a role has the menu permission.

## Vue interaction

The Vue task center provides:

- all-open, claimed, and completed tabs;
- business-key/task-name filtering, paging, refresh, status and overdue indicators;
- task detail with masked business summary;
- category-only supplementation field, attachment, and platform differences;
- immutable domain review records and Flowable activity history;
- claim/unclaim, approve, reject, request-supplement, resubmit, and risk transfer dialogs;
- client-side action visibility based on task state and button permission, backed by server-side validation.

Review forms warn operators not to place sensitive values in opinions. Reject and supplement actions require opinions,
and supplement requests require normalized issue codes.

## Verification

- MySQL 8.4 and PostgreSQL integration tests use the formal onboarding BPMN and verify masked task rows, supplement
  differences, completed-task detail, and immutable review history.
- Vue type checking and production build pass.
- Focused frontend tests verify action visibility and overdue-state behavior.
- Frontend ESLint passes for the task-center API and views.
