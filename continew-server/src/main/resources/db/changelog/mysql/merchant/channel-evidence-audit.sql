-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-evidence-audit-mysql
CREATE TABLE IF NOT EXISTS `biz_channel_evidence_audit` (
    `id`               bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`        bigint(20)   NOT NULL COMMENT '租户ID',
    `channel_code`     varchar(64)  NOT NULL COMMENT '渠道编码',
    `product_code`     varchar(64)  NOT NULL COMMENT '产品编码',
    `config_version`   varchar(64)  NOT NULL COMMENT '连接配置版本',
    `operation`        varchar(64)  NOT NULL COMMENT '渠道操作',
    `business_type`    varchar(64)  NOT NULL COMMENT '业务类型',
    `business_id`      bigint(20)   NOT NULL COMMENT '业务ID',
    `business_version` bigint(20)   NOT NULL COMMENT '业务版本',
    `business_serial`  varchar(128) NOT NULL COMMENT '业务流水号',
    `trace_id`         varchar(64)  NOT NULL COMMENT '链路追踪ID',
    `kyc_version_id`   bigint(20)   NOT NULL COMMENT 'KYC版本ID',
    `object_id`        bigint(20)   NOT NULL COMMENT '附件元数据ID',
    `evidence_type`    varchar(64)  DEFAULT NULL COMMENT '材料类型',
    `object_sha256`    char(64)     DEFAULT NULL COMMENT '对象SHA-256',
    `access_mode`      varchar(32)  NOT NULL COMMENT '访问方式',
    `expires_at`       datetime(3)  DEFAULT NULL COMMENT '访问失效时间',
    `outcome`          varchar(32)  NOT NULL COMMENT '授权结果',
    `failure_category` varchar(64)  DEFAULT NULL COMMENT '净化失败分类',
    `create_time`      datetime(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_channel_evidence_business` (`tenant_id`, `business_type`, `business_id`, `create_time`, `id`),
    KEY `idx_channel_evidence_object` (`tenant_id`, `object_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道材料访问审计';

-- changeset continew:merchant-phase1-channel-evidence-audit-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_channel_evidence_audit_no_update`
    BEFORE UPDATE ON `biz_channel_evidence_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_evidence_audit is append-only';

-- changeset continew:merchant-phase1-channel-evidence-audit-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_channel_evidence_audit_no_delete`
    BEFORE DELETE ON `biz_channel_evidence_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_channel_evidence_audit is append-only';
