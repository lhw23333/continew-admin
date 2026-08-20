-- liquibase formatted sql

-- changeset continew:merchant-phase1-pricing-version-mysql
-- comment 代理商定价版本表
CREATE TABLE IF NOT EXISTS `biz_agent_pricing_version` (
    `id`                        bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`                 bigint(20)    NOT NULL COMMENT '租户ID',
    `agent_id`                  bigint(20)    NOT NULL COMMENT '代理商ID',
    `parent_pricing_version_id` bigint(20)    DEFAULT NULL COMMENT '父级定价版本ID',
    `version_no`                int           NOT NULL COMMENT '版本号',
    `channel_code`              varchar(64)   NOT NULL COMMENT '渠道编码',
    `product_code`              varchar(64)   NOT NULL COMMENT '产品编码',
    `currency`                  char(3)       NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `pricing_rules_json`        text          NOT NULL COMMENT '结构化定价规则，不含密钥或KYC',
    `effective_time`            datetime(3)   NOT NULL COMMENT '生效时间',
    `expires_time`              datetime(3)   DEFAULT NULL COMMENT '失效时间',
    `status`                    varchar(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态',
    `create_user`               bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`               datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`               bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`               datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`                   bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理商定价版本表';

-- changeset continew:merchant-phase1-review-record-mysql
-- comment 审核决策记录表
CREATE TABLE IF NOT EXISTS `biz_review_record` (
    `id`                    bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`             bigint(20)   NOT NULL COMMENT '租户ID',
    `business_type`         varchar(64)  NOT NULL COMMENT '业务类型',
    `business_id`           bigint(20)   NOT NULL COMMENT '业务ID',
    `business_version`      bigint(20)   NOT NULL COMMENT '业务版本',
    `process_instance_id`   varchar(64)  DEFAULT NULL COMMENT 'Flowable流程实例ID',
    `task_id`               varchar(64)  DEFAULT NULL COMMENT 'Flowable任务ID',
    `review_type`           varchar(32)  NOT NULL COMMENT '审核类型（HUMAN/AI）',
    `reviewer_id`           varchar(64)  DEFAULT NULL COMMENT '审核人或模型标识',
    `action`                varchar(32)  NOT NULL COMMENT '审核动作',
    `opinion`               varchar(2000) DEFAULT NULL COMMENT '已净化审核意见',
    `issue_codes_json`      text         DEFAULT NULL COMMENT '补件问题代码',
    `decision_payload_json` text         DEFAULT NULL COMMENT '非敏感决策依据',
    `model_version`         varchar(128) DEFAULT NULL COMMENT '模型或规则版本',
    `evidence_summary`      varchar(2000) DEFAULT NULL COMMENT '已净化证据摘要',
    `decision_time`         datetime(3)  NOT NULL COMMENT '决策时间',
    `create_user`           bigint(20)   DEFAULT NULL COMMENT '创建人',
    `create_time`           datetime(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核决策记录表';

-- changeset continew:merchant-phase1-workflow-mapping-mysql
-- comment 业务对象与Flowable流程映射表
CREATE TABLE IF NOT EXISTS `biz_workflow_instance` (
    `id`                         bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`                  bigint(20)   NOT NULL COMMENT '租户ID',
    `business_type`              varchar(64)  NOT NULL COMMENT '业务类型',
    `business_id`                bigint(20)   NOT NULL COMMENT '业务ID',
    `business_version`           bigint(20)   NOT NULL COMMENT '业务版本',
    `process_definition_key`     varchar(128) NOT NULL COMMENT '流程定义Key',
    `process_definition_version` int          NOT NULL COMMENT '流程定义版本',
    `process_instance_id`        varchar(64)  NOT NULL COMMENT '流程实例ID',
    `business_key`               varchar(255) NOT NULL COMMENT '流程业务Key',
    `workflow_status`            varchar(32)  NOT NULL DEFAULT 'RUNNING' COMMENT '流程映射状态',
    `started_time`               datetime(3)  NOT NULL COMMENT '启动时间',
    `ended_time`                 datetime(3)  DEFAULT NULL COMMENT '结束时间',
    `row_version`                bigint(20)   NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_user`                bigint(20)   DEFAULT NULL COMMENT '创建人',
    `create_time`                datetime(3)  NOT NULL COMMENT '创建时间',
    `update_user`                bigint(20)   DEFAULT NULL COMMENT '修改人',
    `update_time`                datetime(3)  DEFAULT NULL COMMENT '修改时间',
    `deleted`                    bigint(20)   NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务流程映射表';

-- changeset continew:merchant-phase1-limit-adjustment-mysql
-- comment 商户限额调整申请表
CREATE TABLE IF NOT EXISTS `biz_limit_adjustment` (
    `id`                     bigint(20)    NOT NULL COMMENT 'ID',
    `tenant_id`              bigint(20)    NOT NULL COMMENT '租户ID',
    `request_no`             varchar(64)   NOT NULL COMMENT '申请编号',
    `merchant_id`            bigint(20)    NOT NULL COMMENT '商户ID',
    `owning_agent_id`        bigint(20)    NOT NULL COMMENT '归属代理商ID',
    `channel_code`           varchar(64)   NOT NULL COMMENT '渠道编码',
    `platform_code`          varchar(64)   NOT NULL COMMENT '入账平台编码',
    `currency`               char(3)       NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `original_limit`         decimal(20,2) NOT NULL COMMENT '原生效限额',
    `requested_limit`        decimal(20,2) NOT NULL COMMENT '输入申请限额',
    `normalized_limit`       decimal(20,2) NOT NULL COMMENT '规则归一化限额',
    `effective_limit`        decimal(20,2) DEFAULT NULL COMMENT '最终生效限额',
    `reason`                 varchar(1000) NOT NULL COMMENT '申请原因',
    `eligibility_version`    varchar(64)   NOT NULL COMMENT '资格规则版本',
    `channel_config_version` varchar(64)   NOT NULL COMMENT '渠道配置版本',
    `process_instance_id`    varchar(64)   DEFAULT NULL COMMENT '流程实例ID',
    `approval_status`        varchar(32)   NOT NULL DEFAULT 'PENDING' COMMENT '审批状态',
    `channel_status`         varchar(32)   NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT '渠道处理状态',
    `effective_status`       varchar(32)   NOT NULL DEFAULT 'NOT_EFFECTIVE' COMMENT '生效状态',
    `applicant_id`           bigint(20)    NOT NULL COMMENT '申请人',
    `application_time`       datetime(3)   NOT NULL COMMENT '申请时间',
    `approval_time`          datetime(3)   DEFAULT NULL COMMENT '审批时间',
    `effective_time`         datetime(3)   DEFAULT NULL COMMENT '生效时间',
    `opinion`                varchar(2000) DEFAULT NULL COMMENT '已净化审批意见',
    `channel_result_code`    varchar(128)  DEFAULT NULL COMMENT '渠道结果码',
    `channel_result_message` varchar(1000) DEFAULT NULL COMMENT '已净化渠道结果说明',
    `row_version`            bigint(20)    NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_user`            bigint(20)    DEFAULT NULL COMMENT '创建人',
    `create_time`            datetime(3)   NOT NULL COMMENT '创建时间',
    `update_user`            bigint(20)    DEFAULT NULL COMMENT '修改人',
    `update_time`            datetime(3)   DEFAULT NULL COMMENT '修改时间',
    `deleted`                bigint(20)    NOT NULL DEFAULT 0 COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户限额调整申请表';

-- changeset continew:merchant-phase1-outbox-mysql
-- comment 事务型Outbox事件表
CREATE TABLE IF NOT EXISTS `biz_outbox_event` (
    `id`                  bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`           bigint(20)   NOT NULL COMMENT '租户ID',
    `aggregate_type`      varchar(64)  NOT NULL COMMENT '聚合类型',
    `aggregate_id`        bigint(20)   NOT NULL COMMENT '聚合ID',
    `aggregate_version`   bigint(20)   NOT NULL COMMENT '聚合版本',
    `event_type`          varchar(128) NOT NULL COMMENT '事件类型',
    `event_key`           varchar(255) NOT NULL COMMENT '幂等事件Key',
    `payload_json`        text         NOT NULL COMMENT '仅含标识符和非敏感路由数据',
    `headers_json`        text         DEFAULT NULL COMMENT '非敏感事件头',
    `status`              varchar(32)  NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    `retry_count`         int          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time`     datetime(3)  DEFAULT NULL COMMENT '下次重试时间',
    `locked_by`           varchar(128) DEFAULT NULL COMMENT '锁定工作节点',
    `locked_time`         datetime(3)  DEFAULT NULL COMMENT '锁定时间',
    `occurred_time`       datetime(3)  NOT NULL COMMENT '事件发生时间',
    `published_time`      datetime(3)  DEFAULT NULL COMMENT '投递完成时间',
    `last_error_category` varchar(64)  DEFAULT NULL COMMENT '末次错误类别',
    `last_error_message`  varchar(1000) DEFAULT NULL COMMENT '已净化末次错误',
    `trace_id`            varchar(64)  DEFAULT NULL COMMENT '链路追踪ID',
    `create_time`         datetime(3)  NOT NULL COMMENT '创建时间',
    `update_time`         datetime(3)  DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务型Outbox事件表';

-- changeset continew:merchant-phase1-channel-event-mysql
-- comment 渠道回调与查询事件表
CREATE TABLE IF NOT EXISTS `biz_channel_event` (
    `id`                     bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`              bigint(20)   NOT NULL COMMENT '租户ID',
    `channel_code`           varchar(64)  NOT NULL COMMENT '渠道编码',
    `channel_event_id`       varchar(128) DEFAULT NULL COMMENT '渠道事件ID',
    `event_key`              varchar(255) NOT NULL COMMENT '确定性幂等Key',
    `application_id`         bigint(20)   DEFAULT NULL COMMENT '进件申请ID',
    `merchant_id`            bigint(20)   NOT NULL COMMENT '商户ID',
    `business_serial`        varchar(128) NOT NULL COMMENT '渠道业务流水号',
    `event_type`             varchar(64)  NOT NULL COMMENT '事件类型',
    `raw_status`             varchar(128) DEFAULT NULL COMMENT '原始渠道状态码',
    `normalized_state_type`  varchar(64)  DEFAULT NULL COMMENT '归一化子状态类型',
    `normalized_status`      varchar(32)  DEFAULT NULL COMMENT '归一化状态',
    `mapping_version`        varchar(64)  NOT NULL COMMENT '状态映射版本',
    `payload_hash`           char(64)     NOT NULL COMMENT '原始载荷SHA-256',
    `sanitized_payload_json` text         DEFAULT NULL COMMENT '已净化载荷摘要',
    `signature_key_version`  varchar(64)  DEFAULT NULL COMMENT '验签证书/密钥版本',
    `occurred_time`          datetime(3)  DEFAULT NULL COMMENT '渠道事件时间',
    `received_time`          datetime(3)  NOT NULL COMMENT '接收时间',
    `processed_time`         datetime(3)  DEFAULT NULL COMMENT '处理完成时间',
    `processing_status`      varchar(32)  NOT NULL DEFAULT 'RECEIVED' COMMENT '处理状态',
    `retry_count`            int          NOT NULL DEFAULT 0 COMMENT '处理重试次数',
    `last_error_category`    varchar(64)  DEFAULT NULL COMMENT '末次错误类别',
    `last_error_message`     varchar(1000) DEFAULT NULL COMMENT '已净化末次错误',
    `trace_id`               varchar(64)  DEFAULT NULL COMMENT '链路追踪ID',
    `row_version`            bigint(20)   NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_time`            datetime(3)  NOT NULL COMMENT '创建时间',
    `update_time`            datetime(3)  DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道事件表';
