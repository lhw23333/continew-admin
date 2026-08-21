# Private KYC Storage Dependency

Task 4.6 was paused because the repository did not yet define a provider-neutral private-object contract that could be implemented safely without selecting the phase-one storage mode.

## Verified current capability

- X File Storage 2.2.1 exposes ACL checks/setters and presigned URL generation.
- The existing `FileService` uploads to a configured storage and returns/stores a directly constructed URL.
- `sys_storage` contains endpoint, bucket, domain, and credentials but no KYC-private flag, ACL policy, presigned-access policy, or maximum expiry.
- X File Storage ACL values are provider-specific objects rather than one portable `PRIVATE` enum.
- No malware-scanner implementation or approved scan-result contract exists yet.

## Why a generic placeholder is unsafe

- Passing an arbitrary string to `setFileAcl` may be ignored or rejected depending on S3, MinIO, OSS, COS, local, or another provider.
- Treating a normal `FileService` URL as temporary would expose a permanent object location.
- Local storage has no native presigned URL and requires an authenticated streaming/proxy endpoint instead.
- Marking an attachment clean without a real scanner result would violate the KYC attachment requirement.

## Required decision

Select one phase-one private access mode:

1. **S3/MinIO private bucket (recommended):** private object ACL/bucket policy, X File Storage presigned GET, default five-minute expiry, and a provider adapter that rejects unsupported ACL/presign capabilities.
2. **Cloud-specific OSS/COS adapter:** use the selected vendor's ACL and signing types and test against that provider/test double.
3. **Authenticated streaming proxy:** never issue storage URLs; the server streams authorized objects. This also supports local storage but increases application bandwidth and range-request work.

Also select the malware scanner implementation (for example ClamAV/ICAP or a cloud scanning service). The domain will expose a scanner port and fail closed on unavailable/uncertain results, but task 4.6 cannot be marked complete until one real/test-double adapter is configured and verified.

## Safe work that remains after selection

- resolve `kyc_version_id` to merchant ownership server-side and enforce `MerchantScopeAuthorizationService`;
- enforce configured extension, detected MIME, readability, size, and per-evidence/total count limits;
- calculate SHA-256 while streaming;
- persist `biz_kyc_attachment` only with private object ID and validation/scan status;
- issue a bounded access mechanism and append attachment-view security audit by object ID/hash, never file content or permanent URL.

## Resolution

The storage decision was confirmed on August 21, 2026:

- phase one uses a dedicated S3/MinIO private Bucket through X File Storage;
- temporary GET access expires after five minutes by default;
- deployment must explicitly set `KYC_PRIVATE_BUCKET_ACKNOWLEDGED=true` after public access is disabled;
- no malware scanner is currently available, so `NoMalwareScannerAdapter` returns `UNAVAILABLE`, stores the object under the quarantine prefix, and denies every access request until a real scanner reports `CLEAN`;
- KYC uploads intentionally omit generic X File Storage attributes, so `FileRecorderImpl` does not create a `sys_file` entry or expose the object through ordinary file APIs.

The scanner remains a replaceable `MalwareScannerPort`; adding ClamAV/ICAP or a cloud scanner does not require changing the attachment domain service.
