-- liquibase formatted sql

-- changeset continew:merchant-phase1-agent-core-mysql
-- comment 代理商主表与闭包路径表
CREATE TABLE IF NOT EXISTS `biz_agent` (
    `id`                         bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`                  bigint(20)    NOT NULL COMMENT '租户ID',
    `parent_id`                  bigint(20)    NOT NULL DEFAULT 0 COMMENT '直属上级代理商ID',
    `path`                       varchar(1024) NOT NULL DEFAULT '' COMMENT '不可授权信任的展示路径，权限以闭包表为准',
    `user_id`                    bigint(20)    NOT NULL COMMENT '绑定的ContiNew用户ID',
    `agent_no`                   varchar(64)   NOT NULL COMMENT '代理商编号',
    `name`                       varchar(100)  NOT NULL COMMENT '代理商名称',
    `contact_name`               varchar(100)  DEFAULT NULL COMMENT '联系人',
    `contact_mobile_ciphertext`  varbinary(512) DEFAULT NULL COMMENT '联系人手机号密文',
    `contact_mobile_hash`        char(64)      DEFAULT NULL COMMENT '联系人手机号键控哈希',
    `contact_mobile_masked`      varchar(32)   DEFAULT NULL COMMENT '联系人手机号掩码',
    `contact_mobile_key_version` varchar(32)   DEFAULT NULL COMMENT '联系人手机号密钥版本',
    `promotion_code`             varchar(64)   DEFAULT NULL COMMENT '推广码',
    `status`                     varchar(32)   NOT NULL DEFAULT 'ENABLED' COMMENT '生命周期状态',
    `disabled_reason`            varchar(255)  DEFAULT NULL COMMENT '停用原因',
    `row_version`                bigint(20)    NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_user`                bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`                datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`                bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`                datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`                    bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理商表';

CREATE TABLE IF NOT EXISTS `biz_agent_closure` (
    `tenant_id`    bigint(20)  NOT NULL COMMENT '租户ID',
    `ancestor_id`  bigint(20)  NOT NULL COMMENT '祖先代理商ID',
    `descendant_id` bigint(20) NOT NULL COMMENT '后代代理商ID',
    `depth`        int         NOT NULL COMMENT '层级距离，自身为0',
    `create_time`  datetime(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`tenant_id`, `ancestor_id`, `descendant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理商闭包路径表';

-- changeset continew:merchant-phase1-merchant-master-mysql
-- comment 商户主表
CREATE TABLE IF NOT EXISTS `biz_merchant` (
    `id`                         bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`                  bigint(20)    NOT NULL COMMENT '租户ID',
    `owning_agent_id`            bigint(20)    NOT NULL COMMENT '归属代理商ID',
    `merchant_no`                varchar(64)   NOT NULL COMMENT '商户编号',
    `merchant_type`              varchar(32)   NOT NULL COMMENT '商户类型',
    `legal_name`                 varchar(200)  NOT NULL COMMENT '法定主体全称',
    `short_name`                 varchar(100)  NOT NULL COMMENT '商户简称',
    `legal_subject_hash`         char(64)      DEFAULT NULL COMMENT '法定主体归一化键控哈希',
    `operator_user_id`           bigint(20)    NOT NULL COMMENT '商户操作员用户ID',
    `reviewer_user_id`           bigint(20)    NOT NULL COMMENT '商户复核员用户ID',
    `contact_name`               varchar(100)  DEFAULT NULL COMMENT '联系人',
    `contact_mobile_ciphertext`  varbinary(512) DEFAULT NULL COMMENT '联系人手机号密文',
    `contact_mobile_hash`        char(64)      DEFAULT NULL COMMENT '联系人手机号键控哈希',
    `contact_mobile_masked`      varchar(32)   DEFAULT NULL COMMENT '联系人手机号掩码',
    `contact_mobile_key_version` varchar(32)   DEFAULT NULL COMMENT '联系人手机号密钥版本',
    `industry`                   varchar(100)  DEFAULT NULL COMMENT '所属行业',
    `product_description`        varchar(255)  DEFAULT NULL COMMENT '产品描述',
    `status`                     varchar(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '商户生命周期状态',
    `disabled_reason`            varchar(255)  DEFAULT NULL COMMENT '停用原因',
    `certified_kyc_version_id`   bigint(20)    DEFAULT NULL COMMENT '当前认证KYC版本ID',
    `row_version`                bigint(20)    NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_user`                bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`                datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`                bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`                datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`                    bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户主表';

-- changeset continew:merchant-phase1-onboarding-application-mysql
-- comment 商户渠道进件申请表
CREATE TABLE IF NOT EXISTS `biz_onboarding_application` (
    `id`                     bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`              bigint(20)   NOT NULL COMMENT '租户ID',
    `application_no`         varchar(64)  NOT NULL COMMENT '进件申请编号',
    `merchant_id`            bigint(20)   NOT NULL COMMENT '商户ID',
    `owning_agent_id`        bigint(20)   NOT NULL COMMENT '提交时归属代理商ID',
    `channel_code`           varchar(64)  NOT NULL COMMENT '渠道编码',
    `requirement_version`    varchar(64)  NOT NULL COMMENT '渠道材料要求版本',
    `channel_config_version` varchar(64)  DEFAULT NULL COMMENT '渠道配置版本',
    `kyc_version_id`         bigint(20)   DEFAULT NULL COMMENT '当前提交KYC版本ID',
    `idempotency_key`        varchar(128) DEFAULT NULL COMMENT '提交幂等键',
    `status`                 varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '申请状态',
    `reporting_status`       varchar(32)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '报件子状态',
    `agreement_status`       varchar(32)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '签约子状态',
    `card_binding_status`    varchar(32)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '绑卡子状态',
    `reserve_account_status` varchar(32)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '备付金账户子状态',
    `channel_final_status`   varchar(32)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '渠道最终子状态',
    `raw_channel_status`     varchar(128) DEFAULT NULL COMMENT '最近原始渠道状态码',
    `submitted_by`           bigint(20)   DEFAULT NULL COMMENT '提交人',
    `submitted_time`         datetime(3)  DEFAULT NULL COMMENT '提交时间',
    `completed_time`         datetime(3)  DEFAULT NULL COMMENT '完成时间',
    `row_version`            bigint(20)   NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_user`            bigint(20)   DEFAULT NULL COMMENT '创建人',
    `create_time`            datetime(3)  NOT NULL COMMENT '创建时间',
    `update_user`            bigint(20)   DEFAULT NULL COMMENT '修改人',
    `update_time`            datetime(3)  DEFAULT NULL COMMENT '修改时间',
    `deleted`                bigint(20)   NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户渠道进件申请表';

-- changeset continew:merchant-phase1-kyc-version-mysql
-- comment 版本化KYC数据表
CREATE TABLE IF NOT EXISTS `biz_kyc_version` (
    `id`                            bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`                     bigint(20)    NOT NULL COMMENT '租户ID',
    `merchant_id`                   bigint(20)    NOT NULL COMMENT '商户ID',
    `onboarding_application_id`     bigint(20)    DEFAULT NULL COMMENT '进件申请ID',
    `version_no`                    int           NOT NULL COMMENT '业务版本号',
    `previous_version_id`           bigint(20)    DEFAULT NULL COMMENT '补件前版本ID',
    `source_kyc_version_id`         bigint(20)    DEFAULT NULL COMMENT '复用来源KYC版本ID',
    `requirement_version`           varchar(64)   NOT NULL COMMENT '渠道材料要求版本',
    `status`                        varchar(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态',
    `saved_step`                    tinyint       NOT NULL DEFAULT 1 COMMENT '已保存步骤',
    `legal_name`                    varchar(200)  NOT NULL COMMENT '法定主体名称',
    `legal_identifier_ciphertext`   varbinary(512) DEFAULT NULL COMMENT '统一社会信用代码/证件号密文',
    `legal_identifier_hash`         char(64)      DEFAULT NULL COMMENT '法定主体标识键控哈希',
    `legal_identifier_masked`       varchar(64)   DEFAULT NULL COMMENT '法定主体标识掩码',
    `legal_identifier_key_version`  varchar(32)   DEFAULT NULL COMMENT '法定主体标识密钥版本',
    `license_issue_date`            date          DEFAULT NULL COMMENT '营业执照签发日期',
    `license_expiry_date`           date          DEFAULT NULL COMMENT '营业执照到期日期',
    `business_scope`                text          DEFAULT NULL COMMENT '经营范围',
    `address_payload_ciphertext`    longblob      DEFAULT NULL COMMENT '注册地址及经营地址加密载荷',
    `person_payload_ciphertext`     longblob      DEFAULT NULL COMMENT '法人/经办人/受益人加密载荷',
    `shareholder_payload_ciphertext` longblob     DEFAULT NULL COMMENT '股东结构加密载荷',
    `payload_key_version`           varchar(32)   DEFAULT NULL COMMENT '加密载荷密钥版本',
    `settlement_account_ciphertext` varbinary(1024) DEFAULT NULL COMMENT '结算账户密文',
    `settlement_account_hash`       char(64)      DEFAULT NULL COMMENT '结算账户键控哈希',
    `settlement_account_masked`     varchar(64)   DEFAULT NULL COMMENT '结算账户掩码',
    `settlement_key_version`        varchar(32)   DEFAULT NULL COMMENT '结算账户密钥版本',
    `pricing_version_id`            bigint(20)    DEFAULT NULL COMMENT '代理商定价版本ID',
    `frozen_time`                   datetime(3)   DEFAULT NULL COMMENT '提交冻结时间',
    `row_version`                   bigint(20)    NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_user`                   bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`                   datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`                   bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`                   datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`                       bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本化KYC数据表';

-- changeset continew:merchant-phase1-kyc-attachment-mysql
-- comment KYC私有附件元数据表
CREATE TABLE IF NOT EXISTS `biz_kyc_attachment` (
    `id`                bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`         bigint(20)   NOT NULL COMMENT '租户ID',
    `kyc_version_id`    bigint(20)   NOT NULL COMMENT 'KYC版本ID',
    `evidence_type`     varchar(64)  NOT NULL COMMENT '材料类型',
    `storage_object_id` varchar(255) NOT NULL COMMENT '私有存储对象ID',
    `original_name`     varchar(255) NOT NULL COMMENT '原始文件名',
    `extension`         varchar(32)  DEFAULT NULL COMMENT '文件扩展名',
    `declared_mime`     varchar(128) DEFAULT NULL COMMENT '客户端声明MIME',
    `detected_mime`     varchar(128) DEFAULT NULL COMMENT '服务端检测MIME',
    `size_bytes`        bigint(20)   NOT NULL COMMENT '文件大小',
    `sha256`            char(64)     NOT NULL COMMENT '文件SHA-256',
    `scan_status`       varchar(32)  NOT NULL DEFAULT 'PENDING' COMMENT '恶意软件扫描状态',
    `validation_status` varchar(32)  NOT NULL DEFAULT 'PENDING' COMMENT '内容校验状态',
    `sort`              int          NOT NULL DEFAULT 999 COMMENT '排序',
    `create_user`       bigint(20)   DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime(3)  NOT NULL COMMENT '创建时间',
    `update_user`       bigint(20)   DEFAULT NULL COMMENT '修改人',
    `update_time`       datetime(3)  DEFAULT NULL COMMENT '修改时间',
    `deleted`           bigint(20)   NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KYC私有附件元数据表';
