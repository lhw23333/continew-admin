-- liquibase formatted sql

-- changeset continew:merchant-phase1-merchant-management-menu-mysql
-- comment 商户管理动态路由、按钮权限及代理商/商户双岗位授权
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`,
 `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
SELECT source.*
FROM (
    SELECT 690000000000100200 AS `id`, '商户管理' AS `title`, 690000000000100000 AS `parent_id`, 2 AS `type`,
           '/merchant/merchant' AS `path`, 'MerchantMerchant' AS `name`, 'merchant/merchant/index' AS `component`,
           NULL AS `redirect`, 'shop' AS `icon`, b'0' AS `is_external`, b'1' AS `is_cache`, b'0' AS `is_hidden`,
           NULL AS `permission`, 2 AS `sort`, 1 AS `status`, 1 AS `create_user`, NOW() AS `create_time`
    UNION ALL
    SELECT 690000000000100201, '列表', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:merchant:list', 1, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100202, '详情', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:merchant:get', 2, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100203, '新增', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:merchant:create', 3, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100204, '修改', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:merchant:update', 4, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100205, '启停', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:merchant:lifecycle', 5, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100206, '重新核验', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL,
           NULL, NULL, NULL, 'merchant:merchant:reverify', 6, 1, 1, NOW()
) source
LEFT JOIN `sys_menu` existing ON existing.`id` = source.`id`
WHERE existing.`id` IS NULL;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` BETWEEN 690000000000100200 AND 690000000000100206
WHERE role_data.`code` = 'AGENT_ADMIN' AND role_data.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (
    690000000000100000, 690000000000100200, 690000000000100201,
    690000000000100202, 690000000000100204
)
WHERE role_data.`code` = 'MERCHANT_OPERATOR' AND role_data.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (
    690000000000100000, 690000000000100200, 690000000000100201, 690000000000100202
)
WHERE role_data.`code` = 'MERCHANT_REVIEWER' AND role_data.`deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
SELECT package_data.`id`, menu_data.`id`
FROM `tenant_package` package_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (690000000000100000, 690000000000100200)
WHERE package_data.`deleted` = 0;
