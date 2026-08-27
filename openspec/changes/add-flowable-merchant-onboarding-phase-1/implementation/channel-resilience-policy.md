# Channel Transport Resilience Policy

## Versioned per-operation policy

`ChannelTimeoutPolicy`, already stored inside immutable `timeout_json`, now includes a complete
`resiliencePolicies` map for every `ChannelOperation`. Each operation configures:

- maximum attempts, bounded to 1 through 5;
- base retry delay, bounded to one minute;
- consecutive failure threshold for opening the circuit, bounded to 2 through 100;
- circuit open duration, bounded to one second through ten minutes; and
- maximum concurrent calls for the operation bulkhead, bounded to 1 through 100.

Older stored JSON without `resiliencePolicies` remains readable and receives conservative defaults. Query operations
default to three attempts; commands default to one. The operation enum owns the safety classification, and config
validation rejects any attempt to set `maxAttempts > 1` for a non-idempotent command.

Safe queries are:

- onboarding status query;
- masked account-information query; and
- limit-adjustment status query.

Onboarding submission, signing-link creation, and limit adjustment are commands and are never automatically retried.

## Timeout and transmission certainty

`SecureChannelTransport` passes the immutable per-operation timeout to `ChannelTransportClient` on every attempt. A
provider HTTP/SDK client must enforce that deadline and classify timeout/transport exceptions with:

- `NOT_SENT`: failure occurred before bytes could be transmitted;
- `SENT`: request transmission is known to have occurred; or
- `UNKNOWN`: the client cannot prove whether transmission occurred.

For safe queries, `TIMEOUT` and `TRANSPORT_FAILED` are retryable regardless of transmission state. For commands, a
`SENT` or `UNKNOWN` timeout/transport failure becomes `UNCERTAIN_RESULT`; it is audited as `UNCERTAIN` and returned to
the adapter without another attempt. A command failure explicitly marked `NOT_SENT` remains a normal failed result but
still is not automatically retried.

Task 9.8 consumes uncertain commands by querying with the existing business serial or waiting for an authenticated
callback. It never reinterprets `UNCERTAIN_RESULT` as permission to resend.

## Attempt isolation and fresh authentication

`ChannelResilienceExecutor` maintains policy state by tenant, channel, product, immutable config version, and operation.
The bulkhead uses a fair semaphore and rejects excess work immediately with `BULKHEAD_FULL/NOT_SENT`; calls are not
queued in an unbounded executor.

The circuit uses consecutive retryable transport failures:

- closed calls proceed normally;
- reaching the threshold opens the circuit until the configured deadline;
- open calls fail before network I/O with `CIRCUIT_OPEN/NOT_SENT`;
- after the deadline one half-open probe is allowed; and
- a successful probe closes and resets the circuit, while a failed probe reopens it.

Circuit state is deliberately local to one application node. Provider protection remains effective per node without a
distributed coordination dependency; operations should size the configured bulkhead/threshold for the deployed replica
count.

Each retry invokes secure preparation again. Timestamp, nonce, AES-GCM IV/ciphertext, payload digest signature, and
nonce fingerprint are new for every attempt. Each attempt writes its own `PREPARED` plus `FAILED` or `SUCCEEDED` audit
rows. Circuit/bulkhead rejections write a payload-free `REJECTED` row before returning.

## Failure recording

Only timeout, transport, and uncertain-result categories affect the circuit. Configuration, cryptographic preparation,
and audit failures fail closed without poisoning provider health state. Provider exception messages, endpoints, payloads,
signatures, nonces, and key references remain excluded from failures and transport audit.

## Verification

- Policy contract tests verify safe-query defaults and reject unsafe retry overrides.
- Executor tests verify bounded query retry/delay, circuit open and half-open recovery, and concurrent bulkhead rejection
  before an attempt starts.
- Secure transport tests verify fresh authenticated envelopes per retry, no command retry after a sent timeout,
  `UNCERTAIN_RESULT` classification, `NOT_SENT` preservation, and sanitized audit outcomes.
- MySQL 8.4 and PostgreSQL 16 application tests load a custom versioned query policy, execute retry-to-success, prove one
  command attempt after a sent timeout, persist `UNCERTAIN`, and load legacy JSON with conservative defaults.
