-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-product-config-mysql
-- comment 渠道产品资格与材料要求版本，不包含端点、证书或密钥
CREATE TABLE IF NOT EXISTS `biz_channel_product_version` (
    `id`                            bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`                     bigint(20)    NOT NULL COMMENT '租户ID',
    `channel_code`                  varchar(64)   NOT NULL COMMENT '渠道编码',
    `product_code`                  varchar(64)   NOT NULL COMMENT '产品编码',
    `config_version`                varchar(64)   NOT NULL COMMENT '资格配置版本',
    `requirement_version`           varchar(64)   NOT NULL COMMENT '材料要求版本',
    `supported_merchant_types_json` text          NOT NULL COMMENT '支持的商户类型集合',
    `requirement_summary_json`      text          NOT NULL COMMENT '非敏感材料要求摘要',
    `status`                        varchar(32)   NOT NULL DEFAULT 'DISABLED' COMMENT '资格状态',
    `effective_time`                datetime(3)   NOT NULL COMMENT '生效时间',
    `expires_time`                  datetime(3)   DEFAULT NULL COMMENT '失效时间',
    `create_user`                   bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`                   datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`                   bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`                   datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`                       bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_channel_product_config_version`
        (`tenant_id`, `channel_code`, `product_code`, `config_version`, `deleted`),
    INDEX `idx_channel_product_effective`
        (`tenant_id`, `channel_code`, `product_code`, `effective_time`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道产品资格与材料要求版本表';

-- changeset continew:merchant-phase1-channel-product-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_channel_product_no_update`
    BEFORE UPDATE ON `biz_channel_product_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_product_version is append-only';

-- changeset continew:merchant-phase1-channel-product-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_channel_product_no_delete`
    BEFORE DELETE ON `biz_channel_product_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_product_version is append-only';
