-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-transport-audit-mysql
CREATE TABLE IF NOT EXISTS `biz_channel_transport_audit` (
    `id`                     bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`              bigint(20)   NOT NULL COMMENT '租户ID',
    `channel_code`           varchar(64)  NOT NULL COMMENT '渠道编码',
    `product_code`           varchar(64)  NOT NULL COMMENT '产品编码',
    `config_version`         varchar(64)  NOT NULL COMMENT '连接配置版本',
    `operation`              varchar(64)  NOT NULL COMMENT '渠道操作',
    `business_type`          varchar(64)  NOT NULL COMMENT '业务类型',
    `business_id`            bigint(20)   NOT NULL COMMENT '业务ID',
    `business_version`       bigint(20)   NOT NULL COMMENT '业务版本',
    `business_serial`        varchar(128) NOT NULL COMMENT '业务流水号',
    `trace_id`               varchar(64)  NOT NULL COMMENT '链路追踪ID',
    `outcome`                varchar(32)  NOT NULL COMMENT '传输结果',
    `request_time`           datetime(3)  NOT NULL COMMENT '请求时间',
    `response_time`          datetime(3)  DEFAULT NULL COMMENT '响应时间',
    `duration_millis`        bigint(20)   DEFAULT NULL COMMENT '耗时毫秒',
    `nonce_fingerprint`      varchar(64)  DEFAULT NULL COMMENT 'Nonce指纹',
    `signing_key_version`    varchar(64)  DEFAULT NULL COMMENT '签名密钥版本',
    `encryption_key_version` varchar(64)  DEFAULT NULL COMMENT '加密密钥版本',
    `status_code`            int          DEFAULT NULL COMMENT '响应状态码',
    `failure_category`       varchar(64)  DEFAULT NULL COMMENT '净化失败分类',
    `create_time`            datetime(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_channel_transport_business` (`tenant_id`, `business_type`, `business_id`, `create_time`, `id`),
    KEY `idx_channel_transport_trace` (`tenant_id`, `trace_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道出站传输审计';

-- changeset continew:merchant-phase1-channel-transport-audit-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_channel_transport_audit_no_update`
    BEFORE UPDATE ON `biz_channel_transport_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_transport_audit is append-only';

-- changeset continew:merchant-phase1-channel-transport-audit-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_channel_transport_audit_no_delete`
    BEFORE DELETE ON `biz_channel_transport_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_transport_audit is append-only';
