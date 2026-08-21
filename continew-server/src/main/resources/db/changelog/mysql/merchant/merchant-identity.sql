-- liquibase formatted sql

-- changeset continew:merchant-phase1-merchant-reviewer-mobile-mysql
-- comment 商户复核手机号加密字段与商户身份角色
ALTER TABLE `biz_merchant`
    ADD COLUMN `reviewer_mobile_ciphertext` varbinary(512) DEFAULT NULL COMMENT '复核手机号密文'
        AFTER `reviewer_user_id`,
    ADD COLUMN `reviewer_mobile_hash` char(64) DEFAULT NULL COMMENT '复核手机号键控哈希'
        AFTER `reviewer_mobile_ciphertext`,
    ADD COLUMN `reviewer_mobile_hash_key_version` varchar(32) DEFAULT NULL COMMENT '复核手机号哈希密钥版本'
        AFTER `reviewer_mobile_hash`,
    ADD COLUMN `reviewer_mobile_masked` varchar(32) DEFAULT NULL COMMENT '复核手机号掩码'
        AFTER `reviewer_mobile_hash_key_version`,
    ADD COLUMN `reviewer_mobile_key_version` varchar(32) DEFAULT NULL COMMENT '复核手机号数据密钥版本'
        AFTER `reviewer_mobile_masked`;

INSERT INTO `sys_role`
(`id`, `name`, `code`, `data_scope`, `description`, `sort`, `is_system`, `menu_check_strictly`,
 `dept_check_strictly`, `create_user`, `create_time`, `deleted`)
SELECT 690000000000000002, '商户操作员', 'MERCHANT_OPERATOR', 4,
       '商户一期操作员，商户范围由业务授权服务控制', 51, b'1', b'1', b'1', 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `code` = 'MERCHANT_OPERATOR' AND `deleted` = 0);

INSERT INTO `sys_role`
(`id`, `name`, `code`, `data_scope`, `description`, `sort`, `is_system`, `menu_check_strictly`,
 `dept_check_strictly`, `create_user`, `create_time`, `deleted`)
SELECT 690000000000000003, '商户复核员', 'MERCHANT_REVIEWER', 4,
       '商户一期复核员，商户范围由业务授权服务控制', 52, b'1', b'1', b'1', 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `code` = 'MERCHANT_REVIEWER' AND `deleted` = 0);
