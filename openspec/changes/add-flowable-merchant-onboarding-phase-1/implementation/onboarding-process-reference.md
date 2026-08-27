# Onboarding Process QR and Action References

## Bound claims

`OnboardingProcessReferenceService` issues one short-lived HTTPS process URL and a PNG QR code containing that exact
URL. Both representations carry the same signed token. The structured token payload binds:

- tenant ID;
- merchant ID;
- onboarding application ID;
- current persisted channel code;
- intended signing, card-binding, or reserve-account action;
- issued and expiry instants; and
- a cryptographically random nonce so authorized regeneration creates a distinct reference.

The channel adapter `ChannelSigningLinkCommand` also carries an explicit merchant ID in addition to its existing tenant,
application/business, channel, action, and expiry context.

## Integrity and expiry

The token payload uses a versioned binary format with length-prefixed channel/action values instead of delimiter-based
parsing. HMAC-SHA256 uses the approved merchant hash key with the `ONBOARDING_PROCESS_REFERENCE_V1` purpose prefix.
Signatures are compared in constant time before claims are parsed. References expire at the exact boundary and validity
is capped at 30 minutes; the default is ten minutes.

Resolution requires an authenticated caller with current merchant scope. After signature and expiry validation, the
service compares tenant, merchant, and application path values with the signed claims and reloads the application to
confirm its current channel ownership. Tampered tokens, paths, or persisted ownership produce no actionable reference.

The generation endpoint is also the authorized regeneration path. It re-runs merchant scope and application ownership
checks and creates a new nonce. Rotation of the short-lived signing key invalidates outstanding references, which can be
regenerated through that authorized path.

## QR, API, and audit

ZXing 3.5.3 performs standards-based QR encoding with medium error correction; the server returns a Base64 PNG and media
type rather than implementing QR masking or error correction locally. A decoding test proves that the PNG contains the
exact signed HTTPS URL.

Generation and resolution endpoints require `merchant:onboarding:create`. Generic request/response logging omits the
entire `/merchant/**/onboarding-drafts/**` family. Domain `toString()` methods redact the process URL, token, and QR data.
Append-only security audit records contain actor, tenant, application, business version, action, channel, IP, and result,
but never the token, URL, or image.

## Verification

- channel contract tests verify explicit merchant binding and URL redaction;
- token tests cover all bound claims, payload/signature tampering, and exact-boundary expiry;
- service tests cover database ownership checks, path tampering, scoped regeneration, and audit;
- the ZXing adapter test decodes the generated PNG back to the exact URL; and
- MySQL 8.4 and PostgreSQL 16 integration scenarios verify authorized issue/regeneration/resolve and durable audit.
