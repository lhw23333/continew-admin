-- liquibase formatted sql

-- changeset continew:merchant-phase1-merchant-reverification-postgresql
-- comment 商户认证字段变更路由请求，仅保存引用和非敏感元数据
CREATE TABLE IF NOT EXISTS "biz_merchant_reverification_request" (
    "id"                      int8         NOT NULL,
    "tenant_id"               int8         NOT NULL,
    "request_no"              varchar(64)  NOT NULL,
    "merchant_id"             int8         NOT NULL,
    "owning_agent_id"         int8         NOT NULL,
    "target_agent_id"         int8         DEFAULT NULL,
    "source_merchant_version" int8         NOT NULL,
    "change_types_json"       varchar(255) NOT NULL,
    "reason"                  varchar(255) NOT NULL,
    "business_type"           varchar(64)  NOT NULL DEFAULT 'MERCHANT_REVERIFICATION',
    "process_definition_key"  varchar(128) NOT NULL DEFAULT 'merchant-onboarding-review-v1',
    "kyc_version_id"          int8         DEFAULT NULL,
    "status"                  varchar(32)  NOT NULL DEFAULT 'AWAITING_KYC_DRAFT',
    "requested_by"            int8         NOT NULL,
    "requested_time"          timestamp    NOT NULL,
    "row_version"             int8         NOT NULL DEFAULT 0,
    "create_time"             timestamp    NOT NULL,
    "deleted"                 int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_merchant_reverification_request" IS '商户重新核验路由请求表';

CREATE UNIQUE INDEX "uk_merchant_reverification_request_no"
    ON "biz_merchant_reverification_request" ("tenant_id", "request_no");
CREATE INDEX "idx_merchant_reverification_scope_status"
    ON "biz_merchant_reverification_request" ("tenant_id", "owning_agent_id", "status", "requested_time", "id");
