-- liquibase formatted sql

-- changeset continew:merchant-phase1-sensitive-hash-key-versions-postgresql
ALTER TABLE "biz_agent"
    ADD COLUMN "contact_mobile_hash_key_version" varchar(32) DEFAULT NULL;
ALTER TABLE "biz_merchant"
    ADD COLUMN "contact_mobile_hash_key_version" varchar(32) DEFAULT NULL;
ALTER TABLE "biz_kyc_version"
    ADD COLUMN "legal_identifier_hash_key_version" varchar(32) DEFAULT NULL,
    ADD COLUMN "settlement_hash_key_version" varchar(32) DEFAULT NULL;
