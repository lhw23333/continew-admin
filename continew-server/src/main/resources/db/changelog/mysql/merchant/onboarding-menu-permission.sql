-- liquibase formatted sql

-- changeset continew:merchant-phase1-onboarding-menu-permission-mysql
-- comment 商户进件资格查询与后续向导入口权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
SELECT 690000000000100207, '发起进件', 690000000000100200, 3,
       'merchant:onboarding:create', 7, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 690000000000100207);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, 690000000000100207, 0
FROM `sys_role` role_data
WHERE role_data.`code` IN ('AGENT_ADMIN', 'MERCHANT_OPERATOR') AND role_data.`deleted` = 0;
