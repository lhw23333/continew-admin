# Limit Adjustment APIs and Vue Views

Implemented on 2026-08-28 across the authorized backend and frontend repositories.

## Scoped backend APIs

`LimitAdjustmentController` exposes merchant-bound endpoints under
`/merchant/merchants/{merchantId}/limit-adjustments`:

- stable request pagination with request/channel/platform/approval/channel/effective/time filters;
- authorized detail, immutable domain history, and sanitized Flowable history;
- server-side amount-policy preview and explicit confirmation creation;
- claimed human approve/reject actions; and
- claimed channel submit/query execution for `CHANNEL_OPERATIONS` actors.

Every list/detail/history operation first checks the current tenant context and merchant scope. The repository always
uses tenant and merchant predicates and orders pages by `application_time DESC, id DESC`. Detail responses expose the
stable workflow business version and only a task the current actor can view or claim. Task actions are checked again
against the request, process mapping, assignee, role, merchant scope, optimistic row version, and current domain state.

Menu migrations add `merchant:limit:create` and `merchant:limit:list` permissions for MySQL and PostgreSQL. Agent and
merchant operators receive create/history access as configured; reviewer/risk/channel roles receive history access,
while task action permissions continue to use the existing workflow claim/review controls plus server-side role checks.

## Vue views

The merchant list now uses the existing server-derived `ADJUST_LIMIT` and `VIEW_LIMIT_HISTORY` actions to open:

- `LimitAdjustmentModal.vue` — successful-channel selection, amount input, server preview, thousand-rounding display,
  policy version confirmation, reason entry, and stale-preview prevention;
- `LimitHistoryDrawer.vue` — request/status filters, stable pagination, amount/status summaries, and detail navigation;
- `LimitDetailDrawer.vue` — request evidence, independent approval/channel/effective states, immutable history,
  sanitized process history, task claim, review entry, and channel submit/query controls; and
- `LimitReviewModal.vue` — approve/reject with mandatory rejection opinion and a clear warning that approval alone does
  not make the limit effective.

The UI preserves the distinction between `APPROVED` and `EFFECTIVE`, displays the prior effective amount until channel
confirmation, and does not send original-limit, configuration-version, policy-version, or eligibility values supplied
by the client except the exact server preview confirmation fields required by the confirmation contract.

## Verification

- backend reactor compilation and test compilation pass on Java 17;
- frontend `vue-tsc --noEmit`, targeted ESLint, production Vite build, and all 14 Vitest tests pass;
- focused limit workflow/domain tests cover creation, preview, revalidation, approval-not-effective, channel-effective,
  variable policy, and BPMN routing; and
- OpenSpec strict validation remains clean.