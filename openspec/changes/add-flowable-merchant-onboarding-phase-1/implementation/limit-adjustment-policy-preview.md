# Limit Adjustment Policy and Confirmation Preview

## Versioned policy

`biz_limit_adjustment_policy_version` is an append-only policy catalog keyed by tenant, channel, inbound platform, and
currency. Each version retains minimum and maximum values, currency scale, rounding unit, rounding mode, enabled state,
and effective/expiry times. MySQL and PostgreSQL triggers reject updates and deletes, and the catalog selects the latest
enabled version effective at the operation time.

The initial supported rounding mode is `CEILING`, matching the phase-one thousand-unit upward-rounding requirement.
`currency_scale` is bounded by the existing `decimal(20,2)` request schema; policy values and the rounding unit use the
same exact decimal representation.

## Preview

`LimitAdjustmentPreviewService.preview` first rechecks merchant scope and current channel eligibility, then loads the
exact policy dimension. The domain policy:

- rejects values below the configured minimum or above the configured maximum;
- validates significant fractional digits after trailing zero removal against the currency scale;
- divides by the configured unit with `RoundingMode.CEILING` and multiplies back to an exact amount; and
- rejects a rounded result that exceeds the configured maximum.

The returned preview exposes entered and normalized values, whether rounding changed the value, minimum, maximum,
currency precision, rounding unit/mode, and policy version. It does not create or mutate a request.

## Explicit confirmation

Confirmation echoes the preview's normalized value and policy version. The server repeats authorization, eligibility,
policy lookup, and normalization from the original entered amount. A changed policy version or mismatched normalized
value is rejected as stale/tampered; the client never controls the stored normalized amount.

After successful revalidation, confirmation calls the task 10.1 creation boundary with server-computed values. The exact
`amount_policy_version` is persisted on both `biz_limit_adjustment` and every immutable history snapshot and is protected
by the request evidence trigger. This provides the policy reference required for task 10.4 revalidation.

## Verification

- unit tests cover non-thousand preview, unchanged whole-thousand values, minimum/maximum, currency precision,
  post-rounding maximum, valid confirmation, normalized-value tampering, and stale policy versions;
- MySQL 8.4 and PostgreSQL 16 scenarios create a real versioned policy, preview `1250.00` as `2000.00`, confirm the
  stored request, reject tampering, and prove policy immutability; and
- migration round trips prove policy table/trigger creation, rollback, and recreation on both databases.
