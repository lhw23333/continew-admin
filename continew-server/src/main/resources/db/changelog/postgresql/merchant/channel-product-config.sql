-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-product-config-postgresql
-- comment 渠道产品资格与材料要求版本，不包含端点、证书或密钥
CREATE TABLE IF NOT EXISTS "biz_channel_product_version" (
    "id"                            int8         NOT NULL,
    "tenant_id"                     int8         NOT NULL,
    "channel_code"                  varchar(64)  NOT NULL,
    "product_code"                  varchar(64)  NOT NULL,
    "config_version"                varchar(64)  NOT NULL,
    "requirement_version"           varchar(64)  NOT NULL,
    "supported_merchant_types_json" text         NOT NULL,
    "requirement_summary_json"      text         NOT NULL,
    "status"                        varchar(32)  NOT NULL DEFAULT 'DISABLED',
    "effective_time"                timestamp    NOT NULL,
    "expires_time"                  timestamp    DEFAULT NULL,
    "create_user"                   int8         DEFAULT NULL,
    "create_time"                   timestamp    NOT NULL,
    "update_user"                   int8         DEFAULT NULL,
    "update_time"                   timestamp    DEFAULT NULL,
    "deleted"                       int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_channel_product_version" IS '渠道产品资格与材料要求版本表';
CREATE UNIQUE INDEX "uk_channel_product_config_version"
    ON "biz_channel_product_version" ("tenant_id", "channel_code", "product_code", "config_version", "deleted");
CREATE INDEX "idx_channel_product_effective"
    ON "biz_channel_product_version" ("tenant_id", "channel_code", "product_code", "effective_time", "status", "id");

-- changeset continew:merchant-phase1-channel-product-no-mutation-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_channel_product_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'biz_channel_product_version is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_channel_product_no_update
    BEFORE UPDATE ON "biz_channel_product_version"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_channel_product_mutation();
CREATE TRIGGER trg_channel_product_no_delete
    BEFORE DELETE ON "biz_channel_product_version"
    FOR EACH ROW EXECUTE FUNCTION biz_prevent_channel_product_mutation();
