-- liquibase formatted sql

-- changeset continew:merchant-phase1-limit-adjustment-policy-mysql
CREATE TABLE IF NOT EXISTS `biz_limit_adjustment_policy_version` (
    `id`                bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`         bigint(20)    NOT NULL COMMENT '租户ID',
    `channel_code`      varchar(64)   NOT NULL COMMENT '渠道编码',
    `platform_code`     varchar(64)   NOT NULL COMMENT '入账平台编码',
    `currency`          char(3)       NOT NULL COMMENT '币种',
    `policy_version`    varchar(64)   NOT NULL COMMENT '规则版本',
    `minimum_limit`     decimal(20,2) NOT NULL COMMENT '最小申请限额',
    `maximum_limit`     decimal(20,2) NOT NULL COMMENT '最大申请限额',
    `currency_scale`    int           NOT NULL COMMENT '币种小数精度',
    `rounding_unit`     decimal(20,2) NOT NULL COMMENT '向上取整单位',
    `rounding_mode`     varchar(32)   NOT NULL DEFAULT 'CEILING' COMMENT '取整模式',
    `status`            varchar(32)   NOT NULL DEFAULT 'DISABLED' COMMENT '规则状态',
    `effective_time`    datetime(3)   NOT NULL COMMENT '生效时间',
    `expires_time`      datetime(3)   DEFAULT NULL COMMENT '失效时间',
    `create_user`       bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime(3)   NOT NULL COMMENT '创建时间',
    `deleted`           bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_limit_policy_version`
        (`tenant_id`, `channel_code`, `platform_code`, `currency`, `policy_version`, `deleted`),
    KEY `idx_limit_policy_effective`
        (`tenant_id`, `channel_code`, `platform_code`, `currency`, `effective_time`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限额调整规则版本';

-- changeset continew:merchant-phase1-limit-adjustment-policy-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_limit_adjustment_policy_no_update`
    BEFORE UPDATE ON `biz_limit_adjustment_policy_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_limit_adjustment_policy_version is append-only';

-- changeset continew:merchant-phase1-limit-adjustment-policy-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_limit_adjustment_policy_no_delete`
    BEFORE DELETE ON `biz_limit_adjustment_policy_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_limit_adjustment_policy_version is append-only';
