# Channel Adapter Contract

## Boundary

`continew-channel` exposes the project-owned `ChannelAdapter` interface. Merchant, onboarding, workflow, and limit
domains depend only on this contract and normalized DTOs; channel implementations do not expose HTTP-library request
objects, SDK entities, certificates, keys, or provider-specific response classes.

The contract covers:

- onboarding submission;
- onboarding status query;
- short-lived signing/action link generation;
- masked channel account information query;
- limit-adjustment submission; and
- limit-adjustment status query.

The first implementation is the task 9.2 in-memory `SYNTHETIC` adapter. Task 9.3 adds immutable connection versions and
secret-safe loading; adapter registry/orchestration remains a later integration concern.

## Command identity and idempotency

Every operation uses `ChannelCommandContext`, containing:

- explicit tenant ID;
- channel/product dimension;
- immutable channel configuration version;
- business type, ID, and version;
- business serial number; and
- trace ID.

The business serial is the outbound idempotency/correlation identity. A timeout after transmission is represented as an
`UNCERTAIN` result and must be resolved through status query/callback before resend. The DTO contract does not imply that
non-idempotent submissions are retryable.

`ChannelOnboardingSubmitCommand` references the exact submitted KYC version and private evidence object IDs. It does not
carry legal identifiers, mobile numbers, account numbers, binary evidence, permanent URLs, passwords, or serialized KYC
objects. A later authorized orchestration service loads and decrypts only the provider-required fields in controlled
memory.

## Normalized results

`ChannelResultMeta` retains the raw provider status code, mapping version, channel request ID, business serial, config
version, normalized operation status, result time, and a sanitized message. Raw response bodies are not part of the
ordinary result model.

Onboarding results use independent states for:

- reporting;
- signing;
- card binding;
- reserve-account opening; and
- final outcome.

This prevents one provider callback from overwriting unrelated channel progress and prepares task 9.6 non-regression
rules.

Account-information results expose only a channel account reference, masked account number, bank code, and normalized
account status. Signing-link results require HTTPS, reject URL user-info, carry explicit expiry, and redact the URL from
`toString()` output.

Limit commands preserve original, requested, and normalized amounts plus currency, platform, request ID, and normalized
reason code. `EFFECTIVE` results require both an effective amount and effective time; human approval alone is not an
effective channel result.

## Events

`ChannelEvent` is a verified, payload-free event model containing event ID, channel/product, business identity/version,
business serial, provider request/status identifiers, mapping version, normalized operation status, independent
onboarding state, and occurred/received times. Raw callback bodies remain controlled evidence outside this DTO and are
handled by tasks 9.5 and 9.6.

## Verification

Channel contract tests verify exact KYC/evidence version references, defensive collection copying, short-lived URL
redaction, masked account enforcement, limit/business-type validation, and required onboarding event state.

Architecture tests prevent channel API/DTO packages from importing merchant implementations, system implementations,
or Flowable classes. The channel module test suite executes seven tests without adding runtime dependencies.
