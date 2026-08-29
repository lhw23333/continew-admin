-- liquibase formatted sql

-- changeset continew:merchant-foundation-risk-parent-and-channel-operations-mysql
-- comment 修复风险审核父菜单并补齐进件渠道运营角色与按钮权限
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, 690000000000100000, role_data.`tenant_id`
FROM `sys_role` role_data
WHERE role_data.`code` = 'RISK_REVIEWER' AND role_data.`deleted` = 0;

INSERT INTO `sys_role`
(`id`, `name`, `code`, `data_scope`, `description`, `sort`, `is_system`, `menu_check_strictly`,
 `dept_check_strictly`, `create_user`, `create_time`, `deleted`, `tenant_id`)
SELECT 690000000000000005, '渠道运营员', 'CHANNEL_OPERATIONS', 4,
       '商户进件与限额渠道命令执行角色', 54, b'1', b'1', b'1', 1, NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `tenant_id` = 0 AND `code` = 'CHANNEL_OPERATIONS' AND `deleted` = 0);

INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
SELECT source.*
FROM (
    SELECT 690000000000100213 AS `id`, '渠道提交' AS `title`, 690000000000100200 AS `parent_id`, 3 AS `type`,
           'merchant:onboarding:channel:submit' AS `permission`, 13 AS `sort`, 1 AS `status`, 1 AS `create_user`, NOW() AS `create_time`
    UNION ALL
    SELECT 690000000000100214, '渠道查询', 690000000000100200, 3,
           'merchant:onboarding:channel:query', 14, 1, 1, NOW()
) source
LEFT JOIN `sys_menu` existing ON existing.`id` = source.`id`
WHERE existing.`id` IS NULL;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (
    690000000000100000, 690000000000100200, 690000000000100201, 690000000000100202,
    690000000000100209, 690000000000100213, 690000000000100214
)
WHERE role_data.`tenant_id` = 0 AND role_data.`code` = 'CHANNEL_OPERATIONS' AND role_data.`deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
SELECT package_data.`id`, menu_data.`id`
FROM `tenant_package` package_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (690000000000100213, 690000000000100214)
WHERE package_data.`deleted` = 0;
