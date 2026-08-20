-- liquibase formatted sql

-- changeset continew:merchant-phase1-sensitive-hash-key-versions-mysql
ALTER TABLE `biz_agent`
    ADD COLUMN `contact_mobile_hash_key_version` varchar(32) DEFAULT NULL COMMENT '手机号哈希密钥版本'
    AFTER `contact_mobile_hash`;
ALTER TABLE `biz_merchant`
    ADD COLUMN `contact_mobile_hash_key_version` varchar(32) DEFAULT NULL COMMENT '手机号哈希密钥版本'
    AFTER `contact_mobile_hash`;
ALTER TABLE `biz_kyc_version`
    ADD COLUMN `legal_identifier_hash_key_version` varchar(32) DEFAULT NULL COMMENT '主体标识哈希密钥版本'
    AFTER `legal_identifier_hash`,
    ADD COLUMN `settlement_hash_key_version` varchar(32) DEFAULT NULL COMMENT '结算账户哈希密钥版本'
    AFTER `settlement_account_hash`;
