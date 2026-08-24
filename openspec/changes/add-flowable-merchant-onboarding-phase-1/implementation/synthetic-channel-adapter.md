# Synthetic Reference Channel Adapter

## Purpose and isolation

`SyntheticChannelAdapter` implements the complete `ChannelAdapter` contract for automated tests and local integration.
It is a plain in-memory object, is not registered as a Spring production connector, performs no HTTP/filesystem I/O,
loads no secrets, and uses only the channel code `SYNTHETIC`.

Real channel endpoints, credentials, signing, encryption, retry policy, and callbacks remain tasks 9.3 through 9.7.

## Deterministic behavior

The adapter accepts an injected `Clock`, derives stable synthetic provider request IDs from the business serial, and
stores state in concurrent maps. This makes test results repeatable without external infrastructure.

Onboarding submission creates the initial independent state:

- reporting: `PROCESSING`;
- signing/card binding/reserve account: `NOT_STARTED`;
- final outcome: `PROCESSING`.

`advanceOnboarding` is an explicit test control that replaces the normalized state and emits a payload-free
`STATUS_CHANGED` event. `queryOnboardingStatus` accepts a different trace ID but requires the same tenant, channel
product, configuration version, business type/ID/version, and business serial.

Limit adjustment remains `PROCESSING` after submission and becomes `EFFECTIVE` only when the test explicitly calls
`markLimitEffective`. This preserves the rule that approval or command acceptance alone does not change the effective
limit.

Signing-link generation returns a deterministic HTTPS URL with explicit expiry and redacted string rendering. Account
information returns a synthetic reference and masked account number only.

## Idempotency and concurrency

Business serial is the in-memory idempotency key:

- repeating the identical command returns the existing result;
- reusing the serial with different KYC/evidence/limit command data raises `IDEMPOTENCY_CONFLICT`;
- unknown status/query operations raise `NOT_FOUND`;
- commands for a non-`SYNTHETIC` channel raise `UNSUPPORTED_CHANNEL`.

Creation uses atomic concurrent-map computation. Concurrent identical submissions produce one stored record and one
`SUBMISSION_ACCEPTED` event.

## Event control

The adapter emits normalized `ChannelEvent` objects for submission, onboarding status changes, and limit changes.
`drainEvents` returns an immutable snapshot and clears the test queue. Events contain identifiers, raw status code,
mapping version, normalized status/state, and timestamps, but no raw provider payload or KYC data.

## Verification

The channel module now executes twelve tests:

- two architecture boundary tests;
- five normalized channel-contract tests; and
- five synthetic-adapter tests covering idempotency conflict, state query/advance, signing/account responses,
  effective-limit confirmation, and 32-way concurrent duplicate submission.
