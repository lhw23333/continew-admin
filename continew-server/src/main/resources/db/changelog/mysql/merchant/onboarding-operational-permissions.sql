-- liquibase formatted sql

-- changeset continew:merchant-phase1-onboarding-operational-permissions-mysql
-- comment 商户进件草稿与KYC附件操作权限
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
SELECT source.*
FROM (
    SELECT 690000000000100210 AS `id`, '进件草稿' AS `title`, 690000000000100200 AS `parent_id`, 3 AS `type`,
           'merchant:onboarding:draft' AS `permission`, 10 AS `sort`, 1 AS `status`, 1 AS `create_user`, NOW() AS `create_time`
    UNION ALL
    SELECT 690000000000100211, '上传KYC附件', 690000000000100200, 3,
           'merchant:kyc:attachment:upload', 11, 1, 1, NOW()
    UNION ALL
    SELECT 690000000000100212, '查看KYC附件', 690000000000100200, 3,
           'merchant:kyc:attachment:view', 12, 1, 1, NOW()
) source
LEFT JOIN `sys_menu` existing ON existing.`id` = source.`id`
WHERE existing.`id` IS NULL;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (690000000000100210, 690000000000100211, 690000000000100212)
WHERE role_data.`code` = 'AGENT_ADMIN' AND role_data.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, menu_data.`id`, 0
FROM `sys_role` role_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (690000000000100210, 690000000000100211)
WHERE role_data.`code` = 'MERCHANT_OPERATOR' AND role_data.`deleted` = 0;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `tenant_id`)
SELECT role_data.`id`, 690000000000100212, 0
FROM `sys_role` role_data
WHERE role_data.`code` IN ('MERCHANT_REVIEWER', 'RISK_REVIEWER') AND role_data.`deleted` = 0;

INSERT IGNORE INTO `tenant_package_menu` (`package_id`, `menu_id`)
SELECT package_data.`id`, menu_data.`id`
FROM `tenant_package` package_data
JOIN `sys_menu` menu_data ON menu_data.`id` IN (690000000000100200, 690000000000100210, 690000000000100211, 690000000000100212)
WHERE package_data.`deleted` = 0;