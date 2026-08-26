# Verified Channel Callback Boundary

## HTTP boundary and rollout gate

`ChannelCallbackController` exposes the provider callback route:

```text
POST /channel/callbacks/{tenantId}/{channelCode}/{productCode}/{configVersion}
```

The route does not use a user login. Provider authentication is the callback signature, immutable configuration
identity, timestamp, key version, and nonce claim. Generic operation logging is disabled for the method, `/channel/**`
bodies are omitted from persisted access logs, and both signature and nonce headers are redacted.

Required security headers are:

- `X-Channel-Timestamp`: Unix epoch milliseconds;
- `X-Channel-Nonce`: 16 to 128 URL-safe characters;
- `X-Channel-Key-Version`: derived callback key version; and
- `X-Channel-Signature`: URL-safe Base64 HMAC-SHA256 without padding.

`channel.callback.enabled` defaults to `false`. `VerifiedChannelCallback` is wired to idempotent channel-event parsing
and persistence by task 9.6; deployments can now enable the route only for approved test channels and immutable maps.

## Fail-closed verification order

`ChannelCallbackVerifier` performs all checks before returning payload bytes to channel-specific event processing:

1. validate envelope presence, format, payload size, and the five-minute past/future timestamp tolerance;
2. load the exact effective tenant/channel/product/configuration version;
3. resolve only the callback-verification secret, not outbound signing or encryption secrets;
4. compare the presented derived key version in constant time;
5. verify HMAC-SHA256 over the complete callback identity and payload digest;
6. parse the authenticated JSON and require event ID/type, business type/ID/version/serial, raw status, and occurred time;
7. atomically claim the hashed nonce in the database; and
8. append an `ACCEPTED` security audit before returning `VerifiedChannelCallback` to event processing.

Any failure appends a sanitized `REJECTED` security audit and raises a category-only `ChannelCallbackException`. Audit
or replay-store failure prevents the callback from reaching event processing. Provider payload, signature, nonce,
source address, external key reference, and exception messages are never included in the exception or audit row.

The HTTP response is deliberately generic: validation/event rejection returns `400 REJECTED`, infrastructure failure
returns `503 REJECTED`, and both newly applied and duplicate valid events return `202 ACCEPTED`. No sensitive value or
detailed failure category is echoed to the provider.

## Signature contract

The canonical newline-delimited value is:

```text
CALLBACK
tenant ID
channel code
product code
configuration version
normalized timestamp
nonce
callback key version
SHA-256 payload digest
```

The callback key version is `ref-` plus the first 16 hexadecimal characters of SHA-256 over the immutable external
callback key reference. Exact config-version loading supports controlled certificate/key rotation overlap: an older
callback remains acceptable only while its referenced immutable configuration version is still effective.

The current reference algorithm is HMAC-SHA256, matching the synthetic integration baseline. A production adapter with
an asymmetric certificate format can implement the same verified boundary while retaining the key-version, timestamp,
nonce, audit, and required-field rules.

## Replay protection and security evidence

`biz_channel_callback_nonce` stores only tenant/channel/product/config identity, derived callback key version, the full
SHA-256 nonce hash, timestamps, and expiry. MySQL uses `INSERT IGNORE`; PostgreSQL uses `ON CONFLICT DO NOTHING`. The
database unique key makes concurrent claims atomic and returns false for a replay without aborting the PostgreSQL
transaction. Claims are retained for twice the timestamp tolerance; task 9.8 can perform expiry cleanup.

`biz_channel_callback_security_audit` stores only identity, accepted/rejected outcome, bounded failure category, derived
key/fingerprint values, payload hash, source-address fingerprint, and timing. MySQL and PostgreSQL triggers reject every
update and delete. Phase 1 rollback removes both callback tables and the PostgreSQL immutability function.

## Verification

- Channel tests cover valid verification, callback-only secret loading, key-version mismatch, stale timestamp, malformed
  envelope, invalid signature, authenticated missing fields, duplicate nonce, defensive payload copies, audit failure,
  and redacted rendering.
- Controller tests cover constant 202/400/503 acknowledgements without echoing callback data or failure details.
- MySQL 8.4 and PostgreSQL 16 tests prove invalid signatures do not claim a nonce or create a channel event, valid claims
  are persisted once, replays are rejected, raw callback values are absent, and security audits are append-only.
- Both database migration suites execute forward, rollback, and second-forward creation of the callback tables and
  immutability objects.
