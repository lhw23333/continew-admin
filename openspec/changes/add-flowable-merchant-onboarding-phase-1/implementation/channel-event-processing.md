# Idempotent Channel Event Processing

## Transaction boundary

`ChannelEventProcessor` consumes only `VerifiedChannelCallback`; raw HTTP callbacks cannot call event persistence
directly. Processing runs in one database transaction after the 9.5 signature/timestamp/key/nonce checks have committed
their security evidence.

For an onboarding callback the processor:

1. parses authenticated required fields and rejects unsupported business types;
2. computes a deterministic event key from channel/product and provider event ID;
3. returns the existing acknowledgement immediately when the same key and payload hash already exist;
4. loads the exact immutable connection/status-map version;
5. locks the tenant/application and submitted KYC business version with `FOR UPDATE`;
6. validates product, config version, business version, and the bound business serial;
7. atomically claims the event key in `biz_channel_event`;
8. retains the raw status and a safe identifier-only payload summary;
9. maps the raw code through the configuration's recorded mapping version;
10. merges each independent state by progression rank and applies it with application row-version protection; and
11. marks the event `PROCESSED`, `IGNORED_NON_REGRESSION`, or `FAILED`.

The event insert and merchant state update commit together. Unexpected catalog, mapper, or transaction failures are
converted to category-only `PERSISTENCE_FAILED`; no callback body or stored exception detail is returned or logged by
the public controller.

## Idempotency and conflicts

The deterministic key is `CALLBACK:` plus a SHA-256-derived value over channel/product and provider event ID. The
existing unique key on tenant/channel/event key is the final concurrency guard.

- MySQL uses `INSERT IGNORE`.
- PostgreSQL uses `ON CONFLICT DO NOTHING`.
- Same event key and same payload hash returns `DUPLICATE` without another domain update, even if the current KYC version
  has changed since the original event.
- Same event key with a different payload hash raises `EVENT_ID_CONFLICT` and does not overwrite history.

The read-before-lock duplicate check gives stable acknowledgements after later domain changes. The atomic claim remains
in place to close concurrent read/insert races.

## Raw retention and versioned mapping

Each event records provider event ID, event key, product, connection config version, business identity/version/serial,
provider request ID, raw status, payload hash, callback key version, occurred/received time, trace ID, and the immutable
status-mapping version used. `sanitized_payload_json` is rebuilt from approved identifiers; extra callback fields such as
KYC identifiers are discarded.

Mapped events also retain the complete normalized onboarding snapshot, operation status, and progression rank. An
unknown raw status is still stored with `processing_status=FAILED` and `last_error_category=UNMAPPED_STATUS`, but no
merchant state changes. Task 9.8 can surface and repair these records without losing the original raw code.

## Independent non-regression

`biz_onboarding_application` stores separate ranks for reporting, agreement signing, card binding, reserve account, and
final outcome. One mapping rank is applied only to incoming fields that carry information:

- `NOT_STARTED` and `UNKNOWN` never overwrite a known state or raise that field's rank;
- a field changes only when the incoming rank is greater, except initial same-rank information can fill an empty field;
- `SUCCEEDED` is immutable for every sub-state;
- a terminal final state is immutable even if a later callback carries another terminal outcome; and
- raw application status changes only when at least one normalized sub-state changes.

This allows a late report-success event to fill reporting after card binding succeeded, while retaining the newer card
and final states. The complete raw event remains in history whether or not it changed domain state.

The first mapped event binds `channel_business_serial`; later conflicting serials are rejected before event insertion.
Merchant state is owned by `continew-merchant` through `ChannelApplicationStatePort`; channel code never updates the
merchant table directly.

## HTTP acknowledgement and rollout

`ChannelCallbackController` executes verification and event processing inside the tenant context derived from the
signed path tenant ID. Newly applied, ignored-non-regression, and duplicate valid events all receive the same
`202 ACCEPTED`. Semantic conflicts receive generic `400 REJECTED`; persistence/concurrency failures receive generic
`503 REJECTED`.

The route remains feature-gated with `channel.callback.enabled=false` by default. Deployments enable it per approved test
channel after the corresponding immutable configuration and status map exist. Limit-adjustment state application remains
owned by task 10 and is rejected as unsupported in this onboarding processor.

## Verification

- State-merger tests cover late independent progress, immutable successes/final outcomes, and no-information snapshots.
- Controller tests cover event dispatch plus generic validation and persistence failure acknowledgements.
- MySQL 8.4 and PostgreSQL 16 application tests cover out-of-order report/card callbacks, final non-regression, duplicate
  event acknowledgement, event-ID payload conflict, duplicate acknowledgement after KYC version change, wrong business
  version rejection, unmapped raw retention, safe payload summary, and exact mapping-version retention.
- Migration tests execute forward, rollback, and second-forward application of all event/application state columns and
  indexes on both supported databases.
