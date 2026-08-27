-- liquibase formatted sql

-- changeset continew:merchant-phase1-limit-adjustment-foundation-postgresql
ALTER TABLE "biz_limit_adjustment"
    ADD COLUMN "active_request_guard" varchar(32) DEFAULT NULL;

CREATE UNIQUE INDEX "uk_limit_request_no"
    ON "biz_limit_adjustment" ("tenant_id", "request_no");

CREATE UNIQUE INDEX "uk_limit_active_dimension"
    ON "biz_limit_adjustment"
    ("tenant_id", "merchant_id", "channel_code", "platform_code", "active_request_guard");

CREATE TABLE IF NOT EXISTS "biz_limit_adjustment_history" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "request_id" int8 NOT NULL,
    "request_version" int8 NOT NULL,
    "action" varchar(64) NOT NULL,
    "approval_status" varchar(32) NOT NULL,
    "channel_status" varchar(32) NOT NULL,
    "effective_status" varchar(32) NOT NULL,
    "original_limit" numeric(20,2) NOT NULL,
    "requested_limit" numeric(20,2) NOT NULL,
    "normalized_limit" numeric(20,2) NOT NULL,
    "effective_limit" numeric(20,2) DEFAULT NULL,
    "actor_user_id" int8 NOT NULL,
    "opinion" varchar(2000) DEFAULT NULL,
    "channel_result_code" varchar(128) DEFAULT NULL,
    "channel_result_message" varchar(1000) DEFAULT NULL,
    "occurred_time" timestamp NOT NULL,
    PRIMARY KEY ("id")
);
CREATE INDEX "idx_limit_history_request"
    ON "biz_limit_adjustment_history"
    ("tenant_id", "request_id", "request_version", "occurred_time", "id");
COMMENT ON TABLE "biz_limit_adjustment_history" IS '商户限额调整不可变历史';

-- changeset continew:merchant-phase1-limit-adjustment-history-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_limit_adjustment_history_mutation()
RETURNS trigger AS '
BEGIN
    RAISE EXCEPTION ''biz_limit_adjustment_history is append-only'';
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-limit-adjustment-history-immutable-trigger-postgresql
CREATE TRIGGER trg_limit_adjustment_history_immutable
    BEFORE UPDATE OR DELETE ON "biz_limit_adjustment_history"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_limit_adjustment_history_mutation();

-- changeset continew:merchant-phase1-limit-adjustment-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_limit_adjustment_evidence_mutation()
RETURNS trigger AS '
BEGIN
    IF TG_OP = ''DELETE'' THEN
        RAISE EXCEPTION ''biz_limit_adjustment cannot be deleted'';
    END IF;
    IF OLD.tenant_id IS DISTINCT FROM NEW.tenant_id
       OR OLD.request_no IS DISTINCT FROM NEW.request_no
       OR OLD.merchant_id IS DISTINCT FROM NEW.merchant_id
       OR OLD.owning_agent_id IS DISTINCT FROM NEW.owning_agent_id
       OR OLD.channel_code IS DISTINCT FROM NEW.channel_code
       OR OLD.platform_code IS DISTINCT FROM NEW.platform_code
       OR OLD.currency IS DISTINCT FROM NEW.currency
       OR OLD.original_limit IS DISTINCT FROM NEW.original_limit
       OR OLD.requested_limit IS DISTINCT FROM NEW.requested_limit
       OR OLD.normalized_limit IS DISTINCT FROM NEW.normalized_limit
       OR OLD.reason IS DISTINCT FROM NEW.reason
       OR OLD.eligibility_version IS DISTINCT FROM NEW.eligibility_version
       OR OLD.channel_config_version IS DISTINCT FROM NEW.channel_config_version
       OR OLD.applicant_id IS DISTINCT FROM NEW.applicant_id
       OR OLD.application_time IS DISTINCT FROM NEW.application_time
       OR OLD.create_time IS DISTINCT FROM NEW.create_time THEN
        RAISE EXCEPTION ''limit adjustment evidence is immutable'';
    END IF;
    RETURN NEW;
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-limit-adjustment-immutable-trigger-postgresql
CREATE TRIGGER trg_limit_adjustment_evidence_immutable
    BEFORE UPDATE OR DELETE ON "biz_limit_adjustment"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_limit_adjustment_evidence_mutation();
