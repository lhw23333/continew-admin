-- liquibase formatted sql

-- changeset continew:merchant-phase1-merchant-reverification-mysql
-- comment 商户认证字段变更路由请求，仅保存引用和非敏感元数据
CREATE TABLE IF NOT EXISTS `biz_merchant_reverification_request` (
    `id`                     bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`              bigint(20)    NOT NULL COMMENT '租户ID',
    `request_no`             varchar(64)   NOT NULL COMMENT '申请编号',
    `merchant_id`            bigint(20)    NOT NULL COMMENT '商户ID',
    `owning_agent_id`        bigint(20)    NOT NULL COMMENT '申请时归属代理商ID',
    `target_agent_id`        bigint(20)    DEFAULT NULL COMMENT '目标代理商ID，仅归属变更使用',
    `source_merchant_version` bigint(20)   NOT NULL COMMENT '申请时商户版本',
    `change_types_json`      varchar(255)  NOT NULL COMMENT '变更类型，不含敏感值',
    `reason`                 varchar(255)  NOT NULL COMMENT '已净化申请原因',
    `business_type`          varchar(64)   NOT NULL DEFAULT 'MERCHANT_REVERIFICATION' COMMENT '工作流业务类型',
    `process_definition_key` varchar(128)  NOT NULL DEFAULT 'merchant-onboarding-review-v1' COMMENT '复用流程Key',
    `kyc_version_id`         bigint(20)    DEFAULT NULL COMMENT '后续关联KYC草稿版本',
    `status`                 varchar(32)   NOT NULL DEFAULT 'AWAITING_KYC_DRAFT' COMMENT '路由状态',
    `requested_by`           bigint(20)    NOT NULL COMMENT '申请人',
    `requested_time`         datetime(3)   NOT NULL COMMENT '申请时间',
    `row_version`            bigint(20)    NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_time`            datetime(3)   NOT NULL COMMENT '创建时间',
    `deleted`                bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户重新核验路由请求表';

CREATE UNIQUE INDEX `uk_merchant_reverification_request_no`
    ON `biz_merchant_reverification_request` (`tenant_id`, `request_no`);
CREATE INDEX `idx_merchant_reverification_scope_status`
    ON `biz_merchant_reverification_request` (`tenant_id`, `owning_agent_id`, `status`, `requested_time`, `id`);
