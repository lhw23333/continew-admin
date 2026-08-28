-- liquibase formatted sql

-- changeset continew:merchant-phase1-limit-adjustment-menu-permission-mysql
-- comment 商户限额调整创建与历史查询按钮权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
SELECT source.*
FROM (
    SELECT 690000000000100208 AS `id`, '调整限额' AS `title`, 690000000000100200 AS `parent_id`, 3 AS `type`,
           'merchant:limit:create' AS `permission`, 8 AS `sort`, 1 AS `status`, 1 AS `create_user`, NOW() AS `create_time`
    UNION ALL
    SELECT 690000000000100209, '限额历史', 690000000000100200, 3,
           'merchant:limit:list', 9, 1, 1, NOW()
) source
LEFT JOIN `sys_menu` existing ON existing.`id` = source.`id`
WHERE existing.`id` IS NULL;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (690000000000100208, 690000000000100209)
WHERE role_data.`code` IN ('AGENT_ADMIN', 'MERCHANT_OPERATOR') AND role_data.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, 690000000000100209, 0
FROM `sys_role` role_data
WHERE role_data.`code` IN ('MERCHANT_REVIEWER', 'RISK_REVIEWER', 'CHANNEL_OPERATIONS')
  AND role_data.`deleted` = 0;