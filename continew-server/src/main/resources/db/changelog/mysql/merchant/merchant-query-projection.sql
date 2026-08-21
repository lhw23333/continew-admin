-- liquibase formatted sql

-- changeset continew:merchant-phase1-query-projection-mysql
-- comment 商户查询所需非证件类法定代表人名称投影，后续由版本化KYC维护
ALTER TABLE `biz_merchant`
    ADD COLUMN `legal_representative_name` varchar(100) DEFAULT NULL COMMENT '法定代表人姓名查询投影'
    AFTER `legal_subject_hash`;
