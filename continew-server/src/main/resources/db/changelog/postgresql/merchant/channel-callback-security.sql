-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-callback-nonce-postgresql
CREATE TABLE IF NOT EXISTS "biz_channel_callback_nonce" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "channel_code" varchar(64) NOT NULL,
    "product_code" varchar(64) NOT NULL,
    "config_version" varchar(64) NOT NULL,
    "callback_key_version" varchar(64) NOT NULL,
    "nonce_hash" char(64) NOT NULL,
    "received_time" timestamp NOT NULL,
    "expires_time" timestamp NOT NULL,
    "create_time" timestamp NOT NULL,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_channel_callback_nonce"
    ON "biz_channel_callback_nonce"
        ("tenant_id", "channel_code", "product_code", "callback_key_version", "nonce_hash");
CREATE INDEX "idx_channel_callback_nonce_expiry"
    ON "biz_channel_callback_nonce" ("expires_time", "id");
COMMENT ON TABLE "biz_channel_callback_nonce" IS '渠道回调防重放Nonce';

-- changeset continew:merchant-phase1-channel-callback-security-audit-postgresql
CREATE TABLE IF NOT EXISTS "biz_channel_callback_security_audit" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "channel_code" varchar(64) NOT NULL,
    "product_code" varchar(64) NOT NULL,
    "config_version" varchar(64) NOT NULL,
    "outcome" varchar(32) NOT NULL,
    "failure_category" varchar(64) DEFAULT NULL,
    "callback_key_version" varchar(64) DEFAULT NULL,
    "presented_key_fingerprint" varchar(64) DEFAULT NULL,
    "nonce_fingerprint" varchar(64) DEFAULT NULL,
    "payload_hash" char(64) NOT NULL,
    "source_fingerprint" varchar(64) DEFAULT NULL,
    "received_time" timestamp NOT NULL,
    "create_time" timestamp NOT NULL,
    PRIMARY KEY ("id")
);
CREATE INDEX "idx_callback_security_identity_time"
    ON "biz_channel_callback_security_audit"
        ("tenant_id", "channel_code", "product_code", "received_time", "id");
CREATE INDEX "idx_callback_security_outcome_time"
    ON "biz_channel_callback_security_audit" ("outcome", "received_time", "id");
COMMENT ON TABLE "biz_channel_callback_security_audit" IS '渠道回调安全审计';

-- changeset continew:merchant-phase1-channel-callback-security-audit-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_channel_callback_security_audit_mutation()
RETURNS trigger AS '
BEGIN
    RAISE EXCEPTION ''biz_channel_callback_security_audit is append-only'';
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-channel-callback-security-audit-immutable-trigger-postgresql
CREATE TRIGGER trg_channel_callback_security_audit_immutable
    BEFORE UPDATE OR DELETE ON "biz_channel_callback_security_audit"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_channel_callback_security_audit_mutation();
