-- liquibase formatted sql

-- changeset continew:merchant-phase1-limit-adjustment-policy-postgresql
CREATE TABLE IF NOT EXISTS "biz_limit_adjustment_policy_version" (
    "id" int8 NOT NULL,
    "tenant_id" int8 NOT NULL,
    "channel_code" varchar(64) NOT NULL,
    "platform_code" varchar(64) NOT NULL,
    "currency" char(3) NOT NULL,
    "policy_version" varchar(64) NOT NULL,
    "minimum_limit" numeric(20,2) NOT NULL,
    "maximum_limit" numeric(20,2) NOT NULL,
    "currency_scale" int4 NOT NULL,
    "rounding_unit" numeric(20,2) NOT NULL,
    "rounding_mode" varchar(32) NOT NULL DEFAULT 'CEILING',
    "status" varchar(32) NOT NULL DEFAULT 'DISABLED',
    "effective_time" timestamp NOT NULL,
    "expires_time" timestamp DEFAULT NULL,
    "create_user" int8 DEFAULT NULL,
    "create_time" timestamp NOT NULL,
    "deleted" int8 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id"),
    CONSTRAINT "uk_limit_policy_version"
        UNIQUE ("tenant_id", "channel_code", "platform_code", "currency", "policy_version", "deleted")
);
CREATE INDEX "idx_limit_policy_effective"
    ON "biz_limit_adjustment_policy_version"
    ("tenant_id", "channel_code", "platform_code", "currency", "effective_time", "status", "id");
COMMENT ON TABLE "biz_limit_adjustment_policy_version" IS '限额调整规则版本';

-- changeset continew:merchant-phase1-limit-adjustment-policy-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_limit_adjustment_policy_mutation()
RETURNS trigger AS '
BEGIN
    RAISE EXCEPTION ''biz_limit_adjustment_policy_version is append-only'';
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-limit-adjustment-policy-immutable-trigger-postgresql
CREATE TRIGGER trg_limit_adjustment_policy_immutable
    BEFORE UPDATE OR DELETE ON "biz_limit_adjustment_policy_version"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_limit_adjustment_policy_mutation();
