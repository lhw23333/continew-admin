-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-transport-audit-postgresql
CREATE TABLE IF NOT EXISTS "biz_channel_transport_audit" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "channel_code" varchar(64) NOT NULL,
    "product_code" varchar(64) NOT NULL,
    "config_version" varchar(64) NOT NULL,
    "operation" varchar(64) NOT NULL,
    "business_type" varchar(64) NOT NULL,
    "business_id" int8 NOT NULL,
    "business_version" int8 NOT NULL,
    "business_serial" varchar(128) NOT NULL,
    "trace_id" varchar(64) NOT NULL,
    "outcome" varchar(32) NOT NULL,
    "request_time" timestamp NOT NULL,
    "response_time" timestamp DEFAULT NULL,
    "duration_millis" int8 DEFAULT NULL,
    "nonce_fingerprint" varchar(64) DEFAULT NULL,
    "signing_key_version" varchar(64) DEFAULT NULL,
    "encryption_key_version" varchar(64) DEFAULT NULL,
    "status_code" int4 DEFAULT NULL,
    "failure_category" varchar(64) DEFAULT NULL,
    "create_time" timestamp NOT NULL,
    PRIMARY KEY ("id")
);
CREATE INDEX "idx_channel_transport_business"
    ON "biz_channel_transport_audit" ("tenant_id", "business_type", "business_id", "create_time", "id");
CREATE INDEX "idx_channel_transport_trace"
    ON "biz_channel_transport_audit" ("tenant_id", "trace_id", "create_time", "id");
COMMENT ON TABLE "biz_channel_transport_audit" IS '渠道出站传输审计';

-- changeset continew:merchant-phase1-channel-transport-audit-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_channel_transport_audit_mutation()
RETURNS trigger AS '
BEGIN
    RAISE EXCEPTION ''biz_channel_transport_audit is append-only'';
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-channel-transport-audit-immutable-trigger-postgresql
CREATE TRIGGER trg_channel_transport_audit_immutable
    BEFORE UPDATE OR DELETE ON "biz_channel_transport_audit"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_channel_transport_audit_mutation();
