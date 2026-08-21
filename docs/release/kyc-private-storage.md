# KYC Private Storage

Phase-one KYC attachments use a dedicated S3/MinIO private Bucket through X File Storage. The application never returns or persists a permanent attachment URL.

## Required configuration

| Environment variable | Default | Purpose |
|---|---:|---|
| `KYC_PRIVATE_STORAGE_CODE` | empty | X File Storage platform code for the dedicated private Bucket |
| `KYC_PRIVATE_BUCKET_ACKNOWLEDGED` | `false` | Explicit deployment acknowledgement that public read access is disabled |
| `KYC_ATTACHMENT_MAX_SIZE_BYTES` | `10485760` | Maximum attachment size, 10 MiB |
| `KYC_ATTACHMENT_MAX_PER_TYPE` | `5` | Maximum files for one evidence type in a KYC version |
| `KYC_ATTACHMENT_MAX_PER_VERSION` | `20` | Maximum files in one KYC version |
| `KYC_ATTACHMENT_ACCESS_EXPIRY` | `PT5M` | Presigned GET lifetime |

Uploads fail closed when the storage code is missing, the private-Bucket acknowledgement is false, or the configured platform does not support presigned URLs.

## Validation and persistence

- Allowed phase-one extensions are JPG/JPEG, PNG, and PDF.
- Apache Tika detects the actual MIME type; extension, declared MIME, and detected MIME must agree.
- Images must be readable by ImageIO. PDFs must contain a valid PDF header and EOF marker.
- SHA-256 is calculated before metadata persistence.
- `biz_kyc_attachment` stores the opaque storage object reference, MIME metadata, size, hash, validation status, and scan status.
- Uploads omit generic file attributes, so X File Storage does not create a `sys_file` record or ordinary permanent URL.

## No-scanner behavior

No malware scanner is currently configured. The default `NoMalwareScannerAdapter` returns `UNAVAILABLE` and the object is written under the quarantine prefix. Such an attachment cannot receive a temporary access URL.

When a scanner is introduced, implement `MalwareScannerPort` and return `CLEAN`, `INFECTED`, or `UNAVAILABLE`. Only `CLEAN` plus `VALID` attachments are accessible.

## Access controls

The service resolves `kyc_version_id` to its merchant server-side, applies tenant and merchant/agent scope, generates a bounded presigned URL, sets no-store response headers, and appends an immutable `ATTACHMENT_VIEW` security audit without recording the URL or file content.
