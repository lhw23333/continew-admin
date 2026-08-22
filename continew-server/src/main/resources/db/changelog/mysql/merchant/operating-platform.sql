-- liquibase formatted sql

-- changeset continew:merchant-phase1-operating-platform-mysql
-- comment KYC版本内多经营平台记录及独立证明附件关联
CREATE TABLE IF NOT EXISTS `biz_kyc_operating_platform` (
    `id`                bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`         bigint(20)    NOT NULL COMMENT '租户ID',
    `kyc_version_id`    bigint(20)    NOT NULL COMMENT 'KYC版本ID',
    `platform_code`     varchar(64)   NOT NULL COMMENT '平台编码',
    `store_name`        varchar(200)  NOT NULL COMMENT '店铺名称',
    `store_url`         varchar(1000) DEFAULT NULL COMMENT '店铺链接',
    `store_identifier`  varchar(128)  NOT NULL COMMENT '平台店铺标识',
    `certification_status` varchar(32) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '店铺认证状态',
    `row_version`       bigint(20)    NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_user`       bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`       bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`       datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`           bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_kyc_platform_store`
        (`tenant_id`, `kyc_version_id`, `platform_code`, `store_identifier`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KYC经营平台记录表';

CREATE TABLE IF NOT EXISTS `biz_kyc_platform_attachment` (
    `id`                bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`         bigint(20)   NOT NULL COMMENT '租户ID',
    `kyc_version_id`    bigint(20)   NOT NULL COMMENT 'KYC版本ID',
    `platform_id`       bigint(20)   NOT NULL COMMENT '经营平台记录ID',
    `attachment_id`     bigint(20)   NOT NULL COMMENT 'KYC附件ID',
    `evidence_type`     varchar(64)  NOT NULL COMMENT '平台证明类型',
    `create_user`       bigint(20)   DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime(3)  NOT NULL COMMENT '创建时间',
    `update_user`       bigint(20)   DEFAULT NULL COMMENT '修改人',
    `update_time`       datetime(3)  DEFAULT NULL COMMENT '修改时间',
    `deleted`           bigint(20)   NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_platform_attachment` (`tenant_id`, `kyc_version_id`, `attachment_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经营平台证明附件关联表';
