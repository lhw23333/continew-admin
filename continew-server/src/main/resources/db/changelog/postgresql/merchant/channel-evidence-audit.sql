-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-evidence-audit-postgresql
CREATE TABLE IF NOT EXISTS "biz_channel_evidence_audit" (
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
    "kyc_version_id" int8 NOT NULL,
    "object_id" int8 NOT NULL,
    "evidence_type" varchar(64) DEFAULT NULL,
    "object_sha256" char(64) DEFAULT NULL,
    "access_mode" varchar(32) NOT NULL,
    "expires_at" timestamp DEFAULT NULL,
    "outcome" varchar(32) NOT NULL,
    "failure_category" varchar(64) DEFAULT NULL,
    "create_time" timestamp NOT NULL,
    PRIMARY KEY ("id")
);
CREATE INDEX "idx_channel_evidence_business"
    ON "biz_channel_evidence_audit" ("tenant_id", "business_type", "business_id", "create_time", "id");
CREATE INDEX "idx_channel_evidence_object"
    ON "biz_channel_evidence_audit" ("tenant_id", "object_id", "create_time", "id");
COMMENT ON TABLE "biz_channel_evidence_audit" IS '渠道材料访问审计';

-- changeset continew:merchant-phase1-channel-evidence-audit-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_channel_evidence_audit_mutation()
RETURNS trigger AS '
BEGIN
    RAISE EXCEPTION ''biz_channel_evidence_audit is append-only'';
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-channel-evidence-audit-immutable-trigger-postgresql
CREATE TRIGGER trg_channel_evidence_audit_immutable
    BEFORE UPDATE OR DELETE ON "biz_channel_evidence_audit"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_channel_evidence_audit_mutation();
