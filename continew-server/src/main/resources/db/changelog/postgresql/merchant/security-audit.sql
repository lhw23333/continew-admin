-- liquibase formatted sql

-- changeset continew:merchant-phase1-security-audit-postgresql
-- comment 不可变安全审计表
CREATE TABLE IF NOT EXISTS "biz_security_audit" (
    "id"               int8         NOT NULL,
    "tenant_id"        int8         NOT NULL,
    "actor_user_id"    int8         NOT NULL,
    "actor_agent_id"   int8         DEFAULT NULL,
    "action"           varchar(64)  NOT NULL,
    "object_type"      varchar(64)  NOT NULL,
    "object_id"        int8         NOT NULL,
    "business_version" int8         DEFAULT NULL,
    "field_name"       varchar(64)  DEFAULT NULL,
    "reason"           varchar(255) DEFAULT NULL,
    "ip_address"       varchar(64)  DEFAULT NULL,
    "result"           varchar(32)  NOT NULL,
    "failure_code"     varchar(64)  DEFAULT NULL,
    "create_time"      timestamp    NOT NULL,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_security_audit" IS '不可变安全审计表';

CREATE INDEX "idx_security_audit_object_time"
    ON "biz_security_audit" ("tenant_id", "object_type", "object_id", "create_time", "id");
CREATE INDEX "idx_security_audit_actor_time"
    ON "biz_security_audit" ("tenant_id", "actor_user_id", "create_time", "id");

-- changeset continew:merchant-phase1-security-audit-immutable-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_security_audit_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'biz_security_audit is append-only';
END;
$$ LANGUAGE plpgsql;

-- changeset continew:merchant-phase1-security-audit-immutable-trigger-postgresql
CREATE TRIGGER trg_security_audit_immutable
    BEFORE UPDATE OR DELETE ON "biz_security_audit"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_security_audit_mutation();
