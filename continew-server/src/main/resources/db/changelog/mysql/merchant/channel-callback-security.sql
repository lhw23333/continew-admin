-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-callback-nonce-mysql
CREATE TABLE IF NOT EXISTS `biz_channel_callback_nonce` (
    `id`                   bigint(20)  NOT NULL COMMENT 'ID',
    `tenant_id`            bigint(20)  NOT NULL COMMENT '租户ID',
    `channel_code`         varchar(64) NOT NULL COMMENT '渠道编码',
    `product_code`         varchar(64) NOT NULL COMMENT '产品编码',
    `config_version`       varchar(64) NOT NULL COMMENT '连接配置版本',
    `callback_key_version` varchar(64) NOT NULL COMMENT '回调验签密钥版本',
    `nonce_hash`           char(64)    NOT NULL COMMENT 'Nonce SHA-256',
    `received_time`        datetime(3) NOT NULL COMMENT '首次接收时间',
    `expires_time`         datetime(3) NOT NULL COMMENT '清理时间',
    `create_time`          datetime(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_callback_nonce`
        (`tenant_id`, `channel_code`, `product_code`, `callback_key_version`, `nonce_hash`),
    KEY `idx_channel_callback_nonce_expiry` (`expires_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道回调防重放Nonce';

-- changeset continew:merchant-phase1-channel-callback-security-audit-mysql
CREATE TABLE IF NOT EXISTS `biz_channel_callback_security_audit` (
    `id`                        bigint(20)  NOT NULL COMMENT 'ID',
    `tenant_id`                 bigint(20)  NOT NULL COMMENT '租户ID',
    `channel_code`              varchar(64) NOT NULL COMMENT '渠道编码',
    `product_code`              varchar(64) NOT NULL COMMENT '产品编码',
    `config_version`            varchar(64) NOT NULL COMMENT '连接配置版本',
    `outcome`                   varchar(32) NOT NULL COMMENT '验证结果',
    `failure_category`          varchar(64) DEFAULT NULL COMMENT '净化失败分类',
    `callback_key_version`      varchar(64) DEFAULT NULL COMMENT '期望验签密钥版本',
    `presented_key_fingerprint` varchar(64) DEFAULT NULL COMMENT '所报密钥版本指纹',
    `nonce_fingerprint`         varchar(64) DEFAULT NULL COMMENT 'Nonce指纹',
    `payload_hash`              char(64)    NOT NULL COMMENT '载荷SHA-256',
    `source_fingerprint`        varchar(64) DEFAULT NULL COMMENT '来源地址指纹',
    `received_time`             datetime(3) NOT NULL COMMENT '接收时间',
    `create_time`               datetime(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_callback_security_identity_time`
        (`tenant_id`, `channel_code`, `product_code`, `received_time`, `id`),
    KEY `idx_callback_security_outcome_time` (`outcome`, `received_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道回调安全审计';

-- changeset continew:merchant-phase1-channel-callback-security-audit-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_channel_callback_security_audit_no_update`
    BEFORE UPDATE ON `biz_channel_callback_security_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_callback_security_audit is append-only';

-- changeset continew:merchant-phase1-channel-callback-security-audit-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_channel_callback_security_audit_no_delete`
    BEFORE DELETE ON `biz_channel_callback_security_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_callback_security_audit is append-only';
