# Flowable Schema Baseline

## Selected isolation strategy

Phase one uses the Flowable-owned default table prefixes `ACT_` and `FLW_` in the application database. No project-owned domain table may use those prefixes. This keeps one datasource and transaction boundary while making engine tables unambiguous for backup, retention, monitoring, and future migration.

The application disables automatic schema mutation by default:

```yaml
flowable:
  database-schema-update: false
  idm:
    enabled: false
  eventregistry:
    enabled: false
```

The H2 context smoke profile is the only profile allowed to override automatic schema creation. MySQL and PostgreSQL integration profiles keep it disabled and prove that Liquibase creates a schema Flowable can validate and use.

## Included engine schemas

The reviewed migrations contain the Flowable `7.1.0` artifact / `7.1.0.2` engine schema in this order:

1. Common services schema from `org.flowable:flowable-engine-common:7.1.0`.
2. BPMN process engine schema from `org.flowable:flowable-engine:7.1.0`.
3. BPMN process history schema from `org.flowable:flowable-engine:7.1.0`.

Flowable IDM and Event Registry are phase-one non-goals and are disabled. Their `ACT_ID_*` identity-store tables and `FLW_EVENT_*` registry tables are not migrated. Process-engine identity-link tables such as `ACT_RU_IDENTITYLINK` remain required; they store task/process assignment references, not a second user directory.

## Provenance and integrity

The SQL statements are copied from the selected Flowable JAR resources without semantic changes, wrapped with Liquibase formatted-SQL metadata, and normalized only for repository line endings/trailing whitespace. The hashes below identify the original upstream JAR resources.

| Database | Upstream resource | SHA-256 |
|---|---|---|
| MySQL | `flowable.mysql.create.common.sql` | `8d96b9b1b79e23c9220febee3cb5e48d0fc7ac654f67de871a781a88036e285c` |
| MySQL | `flowable.mysql.create.engine.sql` | `3a7ab16f3207c00e1460c0eb51d47fd49e5e81cad6b21b100310a86ba29e2a99` |
| MySQL | `flowable.mysql.create.history.sql` | `b320f2ee05d2b20a6bd6d0076033dc67fc23f8012027598d75b0f4cd7ba00c6e` |
| PostgreSQL | `flowable.postgres.create.common.sql` | `77d0dcd57a6332a01023d751f798ff4c67a99a5260159a21db8ee68445aa0a6b` |
| PostgreSQL | `flowable.postgres.create.engine.sql` | `a87c525b82d9ccc4deee0ccd702e81e3820a9f8df8c318870e4b3365848ba3ae` |
| PostgreSQL | `flowable.postgres.create.history.sql` | `a3bf16ccdbe27529a81e0cd974a21d87068436917f565a436548b4cd5b5bff71` |

Each database migration creates 32 Flowable common/process/history tables. Liquibase records and checksums each common, engine, and history changeset independently.

## Verification

On August 20, 2026:

- MySQL `8.4.0` Testcontainers startup passed with `database-schema-update=false`.
- PostgreSQL `16-alpine` Testcontainers startup passed with `database-schema-update=false`.
- Both tests confirmed a `ProcessEngine` bean exists.
- Both tests confirmed no `IdmEngine` or `EventRegistryEngine` bean exists.

Future Flowable upgrades must add new immutable vendor upgrade changesets after these files. Existing applied SQL must never be edited in place.
