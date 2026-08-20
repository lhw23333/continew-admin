# Phase-One Database Backup and Restore

## Scope and consistency boundary

Back up the complete ContiNew application database, not only `biz_*`, `ACT_*`, or `FLW_*` tables. Merchant records reference ContiNew tenants/users/files, domain rows reference Flowable instances, and Liquibase state is required to identify the applied schema. A partial table-family dump is diagnostic evidence only and is not an approved disaster-recovery backup.

The backup must include at least:

- ContiNew `sys_*`, tenant/plugin, file, audit, and scheduler tables;
- merchant/channel/workflow domain `biz_*` tables;
- Flowable common/process/history `ACT_*` and `FLW_*` tables;
- `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`.

Before a release or restore point:

1. Block new merchant/KYC/limit commands with the phase-one feature flag.
2. Pause Outbox consumers, channel polling/callback state application, Flowable async executor, and scheduled repair jobs.
3. Wait until active database transactions finish and record remaining outbox/job counts.
4. Record application commit, artifact version, Flowable version, Liquibase changelog checksum, database version, timezone, and encryption-key versions.
5. Take one database-consistent snapshot and store it encrypted with access audit.
6. Resume workers only after the backup tool reports success and the backup checksum is stored.

## MySQL backup

Use a dedicated backup account and allow the client to prompt for its password. Do not put credentials in command history.

```powershell
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
mysqldump --host=$env:DB_HOST --port=$env:DB_PORT --user=$env:DB_BACKUP_USER `
  --password --single-transaction --quick --routines --triggers --events --hex-blob `
  --set-gtid-purged=OFF --default-character-set=utf8mb4 `
  --databases $env:DB_NAME --result-file="continew-$stamp.sql"
Get-FileHash -Algorithm SHA256 "continew-$stamp.sql"
```

Validate the dump by restoring it into an isolated MySQL instance of the supported major/minor version:

```powershell
mysql --host=$env:RESTORE_DB_HOST --port=$env:RESTORE_DB_PORT `
  --user=$env:RESTORE_DB_USER --password < "continew-$stamp.sql"
```

## PostgreSQL backup

Use PostgreSQL custom format so restore ordering and parallel validation remain available.

```powershell
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
pg_dump --dbname=$env:DATABASE_URL --format=custom --no-owner --no-acl `
  --file="continew-$stamp.dump"
Get-FileHash -Algorithm SHA256 "continew-$stamp.dump"
pg_restore --list "continew-$stamp.dump" > "continew-$stamp.contents.txt"
```

Restore into an isolated empty database:

```powershell
pg_restore --dbname=$env:RESTORE_DATABASE_URL --clean --if-exists `
  --no-owner --no-acl --single-transaction "continew-$stamp.dump"
```

## Restore validation

Do not point production traffic at a restored database until all checks pass:

1. Verify backup file checksum and restore-tool exit status.
2. Verify Liquibase has no checksum error or pending unexpected changeset.
3. Verify `ACT_GE_PROPERTY` contains `schema.version = 7.1.0.2`.
4. Confirm Flowable IDM and Event Registry tables are absent and the ProcessEngine starts with automatic schema update disabled.
5. Compare tenant, agent, merchant, KYC version, application, workflow mapping, outbox, channel-event, task, and history counts with the recorded backup manifest.
6. Check every active `biz_workflow_instance.process_instance_id` resolves to Flowable runtime or retained history.
7. Check no published outbox event is replayed and no pending event is lost; preserve idempotency keys.
8. Run masked read-only acceptance tests with synthetic/test tenants before enabling workers or user traffic.

## Rollback policy

Application rollback is non-destructive: disable feature flags, stop new process creation, deploy the prior compatible application, and retain all domain and Flowable tables/history. Never run the schema-drop scripts as an operational rollback.

`db/rollback/*/phase1-schema-rollback.sql` exists only for automated empty-database migration round-trip tests. A production destructive rollback requires a separately approved maintenance plan, verified backup, legal/retention approval, and explicit operator authorization.
