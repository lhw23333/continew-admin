-- liquibase formatted sql

-- changeset continew:merchant-phase1-user-must-change-password-mysql
ALTER TABLE `sys_user`
    ADD COLUMN `must_change_password` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必须修改密码'
    AFTER `pwd_reset_time`;

-- changeset continew:merchant-phase1-agent-department-mysql
ALTER TABLE `biz_agent`
    ADD COLUMN `dept_id` bigint(20) DEFAULT NULL COMMENT '对应ContiNew部门ID' AFTER `user_id`,
    ADD COLUMN `remarks` varchar(255) DEFAULT NULL COMMENT '代理商备注' AFTER `contact_mobile_key_version`;
CREATE UNIQUE INDEX `uk_agent_department`
    ON `biz_agent` (`tenant_id`, `dept_id`, `deleted`);

-- changeset continew:merchant-phase1-agent-admin-role-mysql
INSERT INTO `sys_role`
(`id`, `name`, `code`, `data_scope`, `description`, `sort`, `is_system`, `menu_check_strictly`,
 `dept_check_strictly`, `create_user`, `create_time`, `deleted`)
SELECT 690000000000000001, '代理商管理员', 'AGENT_ADMIN', 4,
       '商户一期代理商管理员，业务范围由biz_agent_closure控制', 50, b'1', b'1', b'1', 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `code` = 'AGENT_ADMIN' AND `deleted` = 0);
