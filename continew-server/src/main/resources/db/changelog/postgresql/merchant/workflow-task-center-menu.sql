-- liquibase formatted sql

-- changeset continew:merchant-phase1-workflow-task-center-menu-postgresql
-- comment 审核任务中心动态路由、按钮权限和风险审核角色
INSERT INTO "sys_role"
("id", "name", "code", "data_scope", "description", "sort", "is_system", "menu_check_strictly",
 "dept_check_strictly", "create_user", "create_time", "deleted")
SELECT 690000000000000004, '风险审核员', 'RISK_REVIEWER', 4,
       '商户一期风险审核员，商户范围由业务授权服务控制', 53, true, true, true, 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM "sys_role" WHERE "code" = 'RISK_REVIEWER' AND "deleted" = 0);

INSERT INTO "sys_menu"
("id", "title", "parent_id", "type", "path", "name", "component", "redirect", "icon", "is_external", "is_cache",
 "is_hidden", "permission", "sort", "status", "create_user", "create_time")
VALUES
(690000000000100300, '审核任务中心', 690000000000100000, 2, '/merchant/workflow', 'MerchantWorkflow', 'merchant/workflow/index', NULL, 'check-square', false, true, false, NULL, 3, 1, 1, NOW()),
(690000000000100301, '列表', 690000000000100300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'workflow:task:list', 1, 1, 1, NOW()),
(690000000000100302, '详情', 690000000000100300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'workflow:task:get', 2, 1, 1, NOW()),
(690000000000100303, '认领', 690000000000100300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'workflow:task:claim', 3, 1, 1, NOW()),
(690000000000100304, '审核', 690000000000100300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'workflow:task:review', 4, 1, 1, NOW()),
(690000000000100305, '转派', 690000000000100300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'workflow:task:transfer', 5, 1, 1, NOW()),
(690000000000100306, '历史', 690000000000100300, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'workflow:task:history', 6, 1, 1, NOW())
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (
    690000000000100000, 690000000000100300, 690000000000100301, 690000000000100302,
    690000000000100303, 690000000000100304, 690000000000100306
)
WHERE role_data."code" IN ('MERCHANT_OPERATOR', 'MERCHANT_REVIEWER') AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" BETWEEN 690000000000100300 AND 690000000000100306
WHERE role_data."code" = 'RISK_REVIEWER' AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "tenant_package_menu" ("package_id", "menu_id")
SELECT package_data."id", menu_data."id"
FROM "tenant_package" package_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (690000000000100000, 690000000000100300)
WHERE package_data."deleted" = 0
ON CONFLICT ("package_id", "menu_id") DO NOTHING;
