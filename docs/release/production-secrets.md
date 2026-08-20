# Production Secret Configuration

Production profile values must be injected by environment variables or an approved secret-manager integration. Repository values are not production credentials. The application validates required secrets before creating the Spring bean graph and reports only invalid property names.

## Required production inputs

| Environment variable | Spring property | Purpose |
|---|---|---|
| `DB_PWD` | `spring.datasource.password` | Database credential |
| `REDIS_PWD` | `spring.data.redis.password` | Redis credential |
| `FIELD_ENCRYPTION_PASSWORD` | `continew-starter.encrypt.field.password` | ContiNew symmetric field-encryption key |
| `FIELD_ENCRYPTION_PUBLIC_KEY` | `continew-starter.encrypt.field.public-key` | ContiNew asymmetric public key |
| `FIELD_ENCRYPTION_PRIVATE_KEY` | `continew-starter.encrypt.field.private-key` | ContiNew asymmetric private key |
| `SA_TOKEN_JWT_SECRET` | `sa-token.jwt-secret-key` | Sa-Token JWT signing secret |
| `MERCHANT_DATA_KEY_REF` | `merchant.security.data-key-ref` | Versioned KYC data-key reference |
| `MERCHANT_HASH_KEY_REF` | `merchant.security.hash-key-ref` | Versioned keyed-hash key reference |
| `MERCHANT_CHANNEL_SIGNING_KEY_REF` | `merchant.channel.signing-key-ref` | Channel signing key/certificate reference |
| `MERCHANT_CHANNEL_ENCRYPTION_KEY_REF` | `merchant.channel.encryption-key-ref` | Channel payload encryption key reference |

Key-reference variables contain an opaque KMS/HSM/secret-manager reference, not raw KYC/channel key material. Application components must resolve the reference through an approved provider and must not persist resolved material in Flowable variables, caches, logs, or database configuration rows.

## Optional integrations

- JustAuth is disabled by default in production with `JUSTAUTH_ENABLED=false`. If enabled, provider client IDs and secrets must come from external configuration; no provider credentials are stored in the production YAML.
- SnailJob is disabled by default with `SCHEDULE_ENABLED=false`. When enabled, `SCHEDULE_TOKEN` and `SCHEDULE_PASSWORD` are required and validated; namespace, URL, and username are also externally configurable.

## Deployment rules

1. Inject secrets through the deployment platform without rendering them into Git-tracked files or command-line logs.
2. Restrict read access to the application runtime identity and record secret access/rotation events.
3. Rotate by adding a new versioned key reference, testing decrypt/read compatibility, switching writes, and retaining the previous key only for the approved migration window.
4. Do not print environment dumps, Spring actuator environment values, private keys, passwords, tokens, or resolved KMS material.
5. A production startup failure listing secret property names is a fail-closed condition; do not bypass the validator with example values.
