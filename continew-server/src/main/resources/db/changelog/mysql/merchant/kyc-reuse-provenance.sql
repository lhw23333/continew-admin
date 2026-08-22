-- liquibase formatted sql

-- changeset continew:merchant-phase1-kyc-reuse-provenance-mysql
-- comment 历史KYC复用来源、字段白名单结果和重确认元数据
ALTER TABLE `biz_kyc_version`
    ADD COLUMN `reuse_provenance_json` text DEFAULT NULL COMMENT 'KYC复用来源及字段元数据'
        AFTER `source_kyc_version_id`;
