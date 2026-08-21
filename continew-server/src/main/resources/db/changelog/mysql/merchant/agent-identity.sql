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

-- changeset continew:merchant-phase1-agent-promotion-code-mysql
-- comment 代理商推广码独立状态与租户内唯一约束
ALTER TABLE `biz_agent`
    ADD COLUMN `promotion_code_status` varchar(32) NOT NULL DEFAULT 'DISABLED' COMMENT '推广码状态'
        AFTER `promotion_code`;
CREATE UNIQUE INDEX `uk_agent_promotion_code`
    ON `biz_agent` (`tenant_id`, `promotion_code`, `deleted`);

-- changeset continew:merchant-phase1-agent-management-menu-mysql
-- comment 代理商管理动态路由、按钮权限和代理商管理员授权
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`,
 `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
SELECT source.*
FROM (
    SELECT 690000000000100000 AS `id`, '商户管理' AS `title`, 0 AS `parent_id`, 1 AS `type`,
           '/merchant' AS `path`, 'Merchant' AS `name`, 'Layout' AS `component`, '/merchant/agent' AS `redirect`,
           'shop' AS `icon`, b'0' AS `is_external`, b'0' AS `is_cache`, b'0' AS `is_hidden`,
           NULL AS `permission`, 4 AS `sort`, 1 AS `status`, 1 AS `create_user`, NOW() AS `create_time`
    UNION ALL
    SELECT 690000000000100100, '代理商管理', 690000000000100000, 2, '/merchant/agent', 'MerchantAgent',
           'merchant/agent/index', NULL, 'user-group', b'0', b'1', b'0', NULL, 1, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100101, '列表', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:list', 1, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100102, '详情', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:get', 2, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100103, '新增', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:create', 3, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100104, '修改', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:update', 4, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100105, '启停', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:lifecycle', 5, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100106, '重置密码', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:resetPassword', 6, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100107, '推广码', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:promotionCode', 7, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100108, '定价', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:pricing', 8, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100109, '商户默认', 690000000000100100, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:agent:defaults', 9, 1, 1, NOW()
) source
LEFT JOIN `sys_menu` existing ON existing.`id` = source.`id`
WHERE existing.`id` IS NULL;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (
    690000000000100000, 690000000000100100, 690000000000100101, 690000000000100102,
    690000000000100103, 690000000000100104, 690000000000100105, 690000000000100106,
    690000000000100107, 690000000000100108, 690000000000100109
)
WHERE role_data.`code` = 'AGENT_ADMIN' AND role_data.`deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
SELECT package_data.`id`, menu_data.`id`
FROM `tenant_package` package_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (690000000000100000, 690000000000100100)
WHERE package_data.`deleted` = 0;
