# Limit Adjustment Conflict Revalidation

Implemented on 2026-08-28.

Before an approval can become final and again before a channel submit/query is sent,
`LimitAdjustmentRevalidationService` reloads server-authoritative state and compares it with the immutable request
evidence:

1. the latest effective amount for tenant/merchant/channel/platform/currency must still equal `original_limit`;
2. the current channel-product eligibility version must equal `eligibility_version`;
3. the current connection configuration must equal `channel_config_version`;
4. the current amount policy must equal `amount_policy_version`; and
5. re-normalizing the original requested amount under that policy must still equal `normalized_limit`.

A mismatch fails closed with a sanitized conflict code and leaves the request pending/approved-but-not-submitted rather
than overwriting a newer effective amount or silently adopting new configuration. Rejection remains possible without
revalidation because it cannot change the effective limit.

Focused tests use the applicable current date, 2026-08-28, and cover unchanged evidence, changed effective amount,
changed channel configuration, changed amount-policy version, and changed normalization under the same policy
reference.