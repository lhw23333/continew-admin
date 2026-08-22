-- liquibase formatted sql

-- changeset continew:merchant-phase1-settlement-account-postgresql
-- comment 结算账户模式、验证结果和加密银行信息载荷
ALTER TABLE "biz_kyc_version"
    ADD COLUMN "settlement_mode" varchar(32) DEFAULT NULL,
    ADD COLUMN "settlement_verification_status" varchar(32) DEFAULT NULL,
    ADD COLUMN "settlement_verification_reference" varchar(128) DEFAULT NULL,
    ADD COLUMN "settlement_verifier_version" varchar(64) DEFAULT NULL,
    ADD COLUMN "settlement_verified_time" timestamp DEFAULT NULL,
    ADD COLUMN "settlement_payload_ciphertext" bytea DEFAULT NULL,
    ADD COLUMN "settlement_payload_key_version" varchar(32) DEFAULT NULL;
