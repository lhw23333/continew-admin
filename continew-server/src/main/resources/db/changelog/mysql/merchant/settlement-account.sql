-- liquibase formatted sql

-- changeset continew:merchant-phase1-settlement-account-mysql
-- comment 结算账户模式、验证结果和加密银行信息载荷
ALTER TABLE `biz_kyc_version`
    ADD COLUMN `settlement_mode` varchar(32) DEFAULT NULL COMMENT '结算账户模式'
        AFTER `settlement_key_version`,
    ADD COLUMN `settlement_verification_status` varchar(32) DEFAULT NULL COMMENT '账户验证状态'
        AFTER `settlement_mode`,
    ADD COLUMN `settlement_verification_reference` varchar(128) DEFAULT NULL COMMENT '外部验证引用'
        AFTER `settlement_verification_status`,
    ADD COLUMN `settlement_verifier_version` varchar(64) DEFAULT NULL COMMENT '验证器版本'
        AFTER `settlement_verification_reference`,
    ADD COLUMN `settlement_verified_time` datetime(3) DEFAULT NULL COMMENT '验证时间'
        AFTER `settlement_verifier_version`,
    ADD COLUMN `settlement_payload_ciphertext` longblob DEFAULT NULL COMMENT '户名及银行信息加密载荷'
        AFTER `settlement_verified_time`,
    ADD COLUMN `settlement_payload_key_version` varchar(32) DEFAULT NULL COMMENT '结算信息载荷密钥版本'
        AFTER `settlement_payload_ciphertext`;
