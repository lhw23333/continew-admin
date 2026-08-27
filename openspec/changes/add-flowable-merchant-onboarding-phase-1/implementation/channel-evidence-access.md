# Channel Evidence Access

## Boundary

`ChannelEvidenceAccessPort` is the provider-neutral boundary used by channel adapters to obtain one attachment referenced
by an exact `ChannelOnboardingSubmitCommand`. The command remains payload-free and carries only the submitted KYC
version, requirement version, and opaque attachment metadata IDs.

`ChannelEvidenceAccessService` issues a reference only when:

- the active tenant context matches the command tenant;
- the attachment ID appears in the command evidence allowlist;
- the attachment belongs to the command's exact KYC version; and
- validation and malware scan status both permit access.

The service reuses the dedicated private X File Storage adapter and requests a new presigned GET for each operation. The
channel-specific expiry is the smaller of the configured KYC access expiry and five minutes. Storage object keys and
permanent locations never cross the channel contract.

## Fail-closed audit

Every grant and authorization denial is written through `ChannelEvidenceAuditPort` in a new transaction. A successful
record contains channel/product/config identity, business serial and trace, exact KYC version, attachment metadata ID,
evidence type, SHA-256, access mode, expiry, and outcome. Denials retain the requested ID and sanitized failure category;
metadata that could not be authorized remains null.

`biz_channel_evidence_audit` is append-only in MySQL and PostgreSQL. It deliberately has no URL, storage object ID,
object key, payload, or content column. If audit persistence fails, the service does not return the generated URL.
Diagnostic rendering of `ChannelEvidenceAccess` also redacts the temporary URL.

## Verification

- focused service tests cover exact-version grants, command allowlisting, uncleared/version-mismatched rejection,
  five-minute expiry capping, URL redaction, hash audit, and fail-closed audit errors;
- MySQL 8.4 and PostgreSQL 16 integration tests verify sanitized persistence and update/delete rejection; and
- database migration round-trip tests prove forward creation, phase-one rollback removal, and forward recreation on both
  databases.
