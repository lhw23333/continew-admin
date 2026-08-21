-- liquibase formatted sql

-- changeset continew:merchant-phase1-agent-merchant-default-version-mysql
-- comment 代理商商户默认版本和草稿值快照
CREATE TABLE IF NOT EXISTS `biz_agent_merchant_default_version` (
    `id`                   bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`            bigint(20)    NOT NULL COMMENT '租户ID',
    `agent_id`             bigint(20)    NOT NULL COMMENT '代理商ID',
    `version_no`           int           NOT NULL COMMENT '版本号',
    `default_payload_json` text          NOT NULL COMMENT '默认渠道产品和定价版本引用',
    `effective_time`       datetime(3)   NOT NULL COMMENT '生效时间',
    `expires_time`         datetime(3)   DEFAULT NULL COMMENT '失效时间',
    `status`               varchar(32)   NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本状态',
    `create_user`          bigint(20)    NOT NULL COMMENT '创建人',
    `create_time`          datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`          bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`          datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`              bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理商商户默认版本表';

CREATE UNIQUE INDEX `uk_agent_merchant_default_version`
    ON `biz_agent_merchant_default_version` (`tenant_id`, `agent_id`, `version_no`, `deleted`);
CREATE UNIQUE INDEX `uk_agent_merchant_default_effective`
    ON `biz_agent_merchant_default_version` (`tenant_id`, `agent_id`, `effective_time`, `deleted`);

CREATE TABLE IF NOT EXISTS `biz_kyc_draft_default_snapshot` (
    `id`                       bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`                bigint(20)    NOT NULL COMMENT '租户ID',
    `kyc_version_id`           bigint(20)    NOT NULL COMMENT 'KYC草稿版本ID',
    `agent_default_version_id` bigint(20)    NOT NULL COMMENT '代理商默认版本ID',
    `default_payload_json`     text          NOT NULL COMMENT '复制后的默认值',
    `copied_time`              datetime(3)   NOT NULL COMMENT '复制时间',
    `create_user`              bigint(20)    NOT NULL COMMENT '创建人',
    `create_time`              datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`              bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`              datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`                  bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KYC草稿代理商默认值快照表';

CREATE UNIQUE INDEX `uk_kyc_draft_default_snapshot`
    ON `biz_kyc_draft_default_snapshot` (`tenant_id`, `kyc_version_id`, `deleted`);

-- changeset continew:merchant-phase1-agent-default-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_agent_default_no_update`
    BEFORE UPDATE ON `biz_agent_merchant_default_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_agent_merchant_default_version is append-only';

-- changeset continew:merchant-phase1-agent-default-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_agent_default_no_delete`
    BEFORE DELETE ON `biz_agent_merchant_default_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_agent_merchant_default_version is append-only';

-- changeset continew:merchant-phase1-draft-default-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_draft_default_no_update`
    BEFORE UPDATE ON `biz_kyc_draft_default_snapshot`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_kyc_draft_default_snapshot is append-only';

-- changeset continew:merchant-phase1-draft-default-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_draft_default_no_delete`
    BEFORE DELETE ON `biz_kyc_draft_default_snapshot`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_kyc_draft_default_snapshot is append-only';
