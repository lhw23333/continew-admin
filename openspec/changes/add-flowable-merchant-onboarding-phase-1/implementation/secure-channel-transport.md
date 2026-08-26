# Secure Outbound Channel Transport

## Boundary and execution order

`SecureChannelTransport` is the reusable boundary between normalized channel commands and a provider-specific HTTP or
SDK client. The synthetic adapter remains network-free and does not use this boundary. A production adapter supplies
the serialized plaintext payload and a `ChannelTransportClient` implementation.

Every valid exchange follows this fail-closed order:

1. load the exact effective channel configuration and resolve external secrets;
2. prepare timestamp, nonce, correlation fields, encryption, and signature;
3. append a sanitized `PREPARED` audit row in an independent transaction;
4. invoke the provider client with the configured per-operation timeout; and
5. append a sanitized `SUCCEEDED` or `FAILED` result row before returning or raising an error.

Configuration, cryptographic preparation, or prepared-audit failure prevents the network call. Result-audit failure
also prevents a response from being returned to the adapter. Provider exceptions are converted to a generic
`TRANSPORT_FAILED` category without retaining their messages.

## Authenticated request envelope

`ChannelOutboundRequest` carries the channel command context, operation, HTTPS endpoint, Unix epoch timestamp in
milliseconds, a random 144-bit URL-safe nonce, derived key versions, encrypted flag, payload, and signature. The
business serial and trace ID come from `ChannelCommandContext`, preserving the idempotency and tracing identities from
the normalized adapter contract.

The request redacts endpoint, nonce, payload, and signature from `toString`. Payload access uses defensive copies.

When the versioned configuration has an encryption reference, payloads use AES-256-GCM. SHA-256 derives the 256-bit AES
key from resolved secret material. The binary envelope is:

```text
[format version 0x01][12-byte random IV][ciphertext and 128-bit GCM tag]
```

GCM additional authenticated data binds channel/product, operation, business type/ID/version, business serial, trace
ID, timestamp, and nonce. Configurations without the optional encryption reference send the supplied payload unchanged
but still sign the complete outbound envelope.

Signatures use HMAC-SHA256 and URL-safe Base64 without padding. The canonical newline-delimited value covers, in order:

```text
operation
endpoint path
business serial
trace ID
timestamp
nonce
signing key version
encryption key version or NONE
SHA-256 digest of the transmitted payload
```

Key versions are non-reversible `ref-` fingerprints derived from immutable external key references. Raw references and
resolved material never enter the outbound audit. Temporary key byte arrays are zeroed after cryptographic use, and the
loaded configuration closes all resolved secrets after each exchange.

## Sanitized append-only audit

`biz_channel_transport_audit` records only tenant/channel/product/config identity, operation, business type and
version, business serial, trace ID, outcome, request/response timing, duration, nonce fingerprint, derived key versions,
HTTP status, and a bounded failure category. The schema intentionally has no endpoint, nonce, payload, response body,
signature, certificate, or raw key-reference columns.

Each preparation/result is a separate append-only row. `MyBatisChannelTransportAuditRepository` uses
`REQUIRES_NEW` so evidence is committed independently from the adapter transaction. MySQL and PostgreSQL triggers reject
all updates and deletes. The Phase 1 empty-database rollback removes the table and the PostgreSQL trigger function.

## Verification

- Channel unit tests verify encrypted/signed correlation, payload and endpoint redaction, unavailable-secret rejection
  before network I/O, audit failure before network I/O, and defensive payload/response copies.
- MySQL 8.4 and PostgreSQL 16 application tests verify MyBatis persistence, expected correlation/fingerprint values,
  absence of sensitive transport columns, and database rejection of update/delete attempts.
- Full integration verification also executes both database migration round-trip suites.
