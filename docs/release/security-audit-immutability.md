# Security Audit Immutability

The `biz_security_audit` table is append-only at both the application and database layers.

## Application contract

- Runtime code uses `SecurityAuditRepository.append` only; no update or delete operation exists.
- Reveal audit writes run in an independent transaction so a denied business action cannot roll its audit back.
- Audit rows contain identifiers, sanitized reason, result, failure category, timestamp, and IP only. Complete sensitive values and password proofs are forbidden.

## Database enforcement

- PostgreSQL uses a `BEFORE UPDATE OR DELETE` trigger that always raises an exception.
- MySQL uses separate `BEFORE UPDATE` and `BEFORE DELETE` triggers that always signal an error.

When MySQL binary logging is enabled and `log_bin_trust_function_creators=OFF`, the migration identity needs the privileges required by MySQL to create triggers. Use one of these reviewed deployment options:

1. Apply the Liquibase migration with a dedicated migration/DBA identity that can create triggers.
2. During the controlled migration window, set `log_bin_trust_function_creators=ON`, apply and verify the triggers, then restore the approved database setting.

The runtime application identity does not need trigger-creation privileges. Production rollout must verify both triggers exist before enabling privileged reveal.

## Verification

The MySQL 8.4 and PostgreSQL 16 Testcontainers tests insert an audit event and prove direct `UPDATE` and `DELETE` statements fail while the original row remains unchanged.
