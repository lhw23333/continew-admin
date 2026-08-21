-- liquibase formatted sql

-- changeset continew:merchant-phase1-security-audit-mysql
-- comment 不可变安全审计表
CREATE TABLE IF NOT EXISTS `biz_security_audit` (
    `id`               bigint(20)   NOT NULL COMMENT 'ID',
    `tenant_id`        bigint(20)   NOT NULL COMMENT '租户ID',
    `actor_user_id`    bigint(20)   NOT NULL COMMENT '操作用户ID',
    `actor_agent_id`   bigint(20)   DEFAULT NULL COMMENT '操作用户代理商ID',
    `action`           varchar(64)  NOT NULL COMMENT '安全动作',
    `object_type`      varchar(64)  NOT NULL COMMENT '对象类型',
    `object_id`        bigint(20)   NOT NULL COMMENT '对象ID',
    `business_version` bigint(20)   DEFAULT NULL COMMENT '业务版本',
    `field_name`       varchar(64)  DEFAULT NULL COMMENT '敏感字段名称',
    `reason`           varchar(255) DEFAULT NULL COMMENT '脱敏后的操作理由',
    `ip_address`       varchar(64)  DEFAULT NULL COMMENT '客户端IP',
    `result`           varchar(32)  NOT NULL COMMENT '结果',
    `failure_code`     varchar(64)  DEFAULT NULL COMMENT '失败分类',
    `create_time`      datetime(3)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变安全审计表';

CREATE INDEX `idx_security_audit_object_time`
    ON `biz_security_audit` (`tenant_id`, `object_type`, `object_id`, `create_time`, `id`);
CREATE INDEX `idx_security_audit_actor_time`
    ON `biz_security_audit` (`tenant_id`, `actor_user_id`, `create_time`, `id`);

-- changeset continew:merchant-phase1-security-audit-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_security_audit_no_update`
    BEFORE UPDATE ON `biz_security_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_security_audit is append-only';

-- changeset continew:merchant-phase1-security-audit-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_security_audit_no_delete`
    BEFORE DELETE ON `biz_security_audit`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_security_audit is append-only';
