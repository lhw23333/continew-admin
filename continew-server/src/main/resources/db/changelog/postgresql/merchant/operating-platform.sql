-- liquibase formatted sql

-- changeset continew:merchant-phase1-operating-platform-postgresql
-- comment KYC版本内多经营平台记录及独立证明附件关联
CREATE TABLE IF NOT EXISTS "biz_kyc_operating_platform" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "kyc_version_id" int8 NOT NULL,
    "platform_code" varchar(64) NOT NULL, "store_name" varchar(200) NOT NULL,
    "store_url" varchar(1000) DEFAULT NULL, "store_identifier" varchar(128) NOT NULL,
    "certification_status" varchar(32) NOT NULL DEFAULT 'UNVERIFIED',
    "row_version" int8 NOT NULL DEFAULT 0, "create_user" int8 DEFAULT NULL,
    "create_time" timestamp NOT NULL, "update_user" int8 DEFAULT NULL,
    "update_time" timestamp DEFAULT NULL, "deleted" int8 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_kyc_platform_store"
    ON "biz_kyc_operating_platform" ("tenant_id", "kyc_version_id", "platform_code", "store_identifier", "deleted");

CREATE TABLE IF NOT EXISTS "biz_kyc_platform_attachment" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "kyc_version_id" int8 NOT NULL,
    "platform_id" int8 NOT NULL, "attachment_id" int8 NOT NULL,
    "evidence_type" varchar(64) NOT NULL, "create_user" int8 DEFAULT NULL,
    "create_time" timestamp NOT NULL, "update_user" int8 DEFAULT NULL,
    "update_time" timestamp DEFAULT NULL, "deleted" int8 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_platform_attachment"
    ON "biz_kyc_platform_attachment" ("tenant_id", "kyc_version_id", "attachment_id", "deleted");
