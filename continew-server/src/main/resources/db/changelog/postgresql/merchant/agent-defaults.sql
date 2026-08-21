-- liquibase formatted sql

-- changeset continew:merchant-phase1-agent-merchant-default-version-postgresql
-- comment 代理商商户默认版本和草稿值快照
CREATE TABLE IF NOT EXISTS "biz_agent_merchant_default_version" (
    "id"                   int8         NOT NULL,
    "tenant_id"            int8         NOT NULL,
    "agent_id"             int8         NOT NULL,
    "version_no"           int4         NOT NULL,
    "default_payload_json" text         NOT NULL,
    "effective_time"       timestamp    NOT NULL,
    "expires_time"         timestamp    DEFAULT NULL,
    "status"               varchar(32)  NOT NULL DEFAULT 'PUBLISHED',
    "create_user"          int8         NOT NULL,
    "create_time"          timestamp    NOT NULL,
    "update_user"          int8         DEFAULT NULL,
    "update_time"          timestamp    DEFAULT NULL,
    "deleted"              int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_agent_merchant_default_version" IS '代理商商户默认版本表';

CREATE UNIQUE INDEX "uk_agent_merchant_default_version"
    ON "biz_agent_merchant_default_version" ("tenant_id", "agent_id", "version_no", "deleted");
CREATE UNIQUE INDEX "uk_agent_merchant_default_effective"
    ON "biz_agent_merchant_default_version" ("tenant_id", "agent_id", "effective_time", "deleted");

CREATE TABLE IF NOT EXISTS "biz_kyc_draft_default_snapshot" (
    "id"                       int8         NOT NULL,
    "tenant_id"                int8         NOT NULL,
    "kyc_version_id"           int8         NOT NULL,
    "agent_default_version_id" int8         NOT NULL,
    "default_payload_json"     text         NOT NULL,
    "copied_time"              timestamp    NOT NULL,
    "create_user"              int8         NOT NULL,
    "create_time"              timestamp    NOT NULL,
    "update_user"              int8         DEFAULT NULL,
    "update_time"              timestamp    DEFAULT NULL,
    "deleted"                  int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_kyc_draft_default_snapshot" IS 'KYC草稿代理商默认值快照表';

CREATE UNIQUE INDEX "uk_kyc_draft_default_snapshot"
    ON "biz_kyc_draft_default_snapshot" ("tenant_id", "kyc_version_id", "deleted");

-- changeset continew:merchant-phase1-agent-default-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_agent_default_mutation()
RETURNS trigger AS '
BEGIN
    RAISE EXCEPTION ''agent merchant defaults are append-only'';
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-agent-default-immutable-triggers-postgresql
CREATE TRIGGER trg_agent_default_immutable
    BEFORE UPDATE OR DELETE ON "biz_agent_merchant_default_version"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_agent_default_mutation();
CREATE TRIGGER trg_draft_default_immutable
    BEFORE UPDATE OR DELETE ON "biz_kyc_draft_default_snapshot"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_agent_default_mutation();
