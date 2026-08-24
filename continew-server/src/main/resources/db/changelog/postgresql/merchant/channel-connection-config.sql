-- liquibase formatted sql
-- changeset continew:merchant-phase1-channel-connection-config-postgresql
CREATE TABLE IF NOT EXISTS "biz_channel_connection_version" (
 "id" int8 NOT NULL, "tenant_id" int8 NOT NULL,
 "channel_code" varchar(64) NOT NULL, "product_code" varchar(64) NOT NULL, "config_version" varchar(64) NOT NULL,
 "endpoint_json" text NOT NULL, "timeout_json" text NOT NULL,
 "status_mapping_version" varchar(64) NOT NULL, "status_mapping_json" text NOT NULL,
 "signing_key_ref" varchar(255) NOT NULL, "encryption_key_ref" varchar(255) DEFAULT NULL,
 "callback_verification_key_ref" varchar(255) NOT NULL,
 "status" varchar(32) NOT NULL DEFAULT 'DISABLED', "effective_time" timestamp NOT NULL,
 "expires_time" timestamp DEFAULT NULL, "create_user" int8 DEFAULT NULL,
 "create_time" timestamp NOT NULL, "update_user" int8 DEFAULT NULL,
 "update_time" timestamp DEFAULT NULL, "deleted" int8 NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_channel_connection_version" ON "biz_channel_connection_version" ("tenant_id","channel_code","product_code","config_version","deleted");
CREATE INDEX "idx_channel_connection_effective" ON "biz_channel_connection_version" ("tenant_id","channel_code","product_code","status","effective_time","id");
-- changeset continew:merchant-phase1-channel-connection-no-mutation-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_channel_connection_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'biz_channel_connection_version is append-only'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_channel_connection_immutable BEFORE UPDATE OR DELETE ON "biz_channel_connection_version" FOR EACH ROW EXECUTE FUNCTION biz_prevent_channel_connection_mutation();
