-- liquibase formatted sql

-- changeset continew:merchant-phase1-agent-pricing-uniqueness-postgresql
-- comment 代理商定价版本号和生效时点租户内唯一
CREATE UNIQUE INDEX "uk_agent_pricing_version"
    ON "biz_agent_pricing_version"
    ("tenant_id", "agent_id", "channel_code", "product_code", "currency", "version_no", "deleted");
CREATE UNIQUE INDEX "uk_agent_pricing_effective_time"
    ON "biz_agent_pricing_version"
    ("tenant_id", "agent_id", "channel_code", "product_code", "currency", "effective_time", "deleted");

-- changeset continew:merchant-phase1-agent-pricing-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_agent_pricing_mutation()
RETURNS trigger AS '
BEGIN
    RAISE EXCEPTION ''biz_agent_pricing_version is append-only'';
END;
' LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-agent-pricing-immutable-trigger-postgresql
CREATE TRIGGER trg_agent_pricing_immutable
    BEFORE UPDATE OR DELETE ON "biz_agent_pricing_version"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_agent_pricing_mutation();
