# Versioned Channel Connection Configuration

## Separate configuration domains

Channel eligibility/material requirements remain in append-only `biz_channel_product_version`. Transport and security
metadata live in the separate append-only `biz_channel_connection_version` table. Both use the same tenant,
channel/product, and `configVersion` dimension but serve different concerns.

A connection version contains:

- HTTPS base endpoint and one relative path for every `ChannelOperation`;
- bounded connect/read/per-operation timeouts;
- status-mapping version and raw-code-to-normalized-state mapping;
- signing, optional encryption, and callback-verification key references;
- enabled/disabled state and effective/expiry times; and
- immutable creation audit fields.

MySQL and PostgreSQL triggers reject every update and delete. Endpoint, timeout, mapping, or key-reference changes require
a new config version.

## Endpoint and timeout validation

Base endpoints must use HTTPS, contain a host, and cannot contain URL user-info, query, or fragments. Operation paths
must be relative absolute paths beginning with `/`, cannot contain traversal, query, fragments, or control characters,
and must cover all six adapter operations.

Connect, read, and per-operation timeouts must be positive and no longer than ten minutes. Operation maps must contain
every operation so that a new adapter method cannot silently inherit an unsafe default.

## Versioned status maps

`ChannelStatusMapping` maps each raw provider status code to:

- normalized operation status;
- onboarding independent sub-states and/or limit status;
- progression rank; and
- terminal indicator.

The mapping version is recorded separately from the connection version and is retained in normalized results/events.
Task 9.6 uses the progression rank and terminal indicator for idempotent non-regression rules.

## External key references

The database accepts only references matching `env://`, `vault://`, or `kms://`; inline plaintext/Base64 key material is
rejected. Required references are signing and callback verification; encryption is optional per channel version.

`EnvironmentChannelSecretProvider` currently resolves only `env://` references containing Base64 material. Unsupported,
missing, malformed, undersized, or oversized material fails closed with the generic message `Channel secret material is
unavailable`; neither the reference nor material appears in the exception.

`ChannelSecret` defensively copies bytes, redacts `toString`, and zeroes its internal byte array when closed.
`LoadedChannelConfiguration` closes all successfully resolved secrets, including partial resolution failures.
Vault/KMS provider implementations can be added without changing stored config or adapter contracts.

## Loading policy

`ChannelConfigurationLoader` loads an exact immutable version, verifies it is enabled and effective at the requested
time, resolves required external secrets, and returns a closeable `LoadedChannelConfiguration`. It never falls forward
to a newer version when a business command references an older configuration version.

## Verification

- The channel module runs sixteen model, architecture, synthetic-adapter, and configuration-loader tests.
- Server tests verify environment secret resolution and redaction.
- MySQL 8.4 and PostgreSQL integration tests verify exact/effective version selection, disabled-newer-version exclusion,
  three secret resolutions, reference-only storage, and append-only update/delete rejection.
- No production channel endpoint or key material is included in fixtures.
