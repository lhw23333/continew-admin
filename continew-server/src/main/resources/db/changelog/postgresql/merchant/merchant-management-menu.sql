-- liquibase formatted sql

-- changeset continew:merchant-phase1-merchant-management-menu-postgresql
-- comment 商户管理动态路由、按钮权限及代理商/商户双岗位授权
INSERT INTO "sys_menu"
("id", "title", "parent_id", "type", "path", "name", "component", "redirect", "icon", "is_external",
 "is_cache", "is_hidden", "permission", "sort", "status", "create_user", "create_time")
VALUES
(690000000000100200, '商户管理', 690000000000100000, 2, '/merchant/merchant', 'MerchantMerchant',
 'merchant/merchant/index', NULL, 'shop', false, true, false, NULL, 2, 1, 1, NOW()),
(690000000000100201, '列表', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'merchant:merchant:list', 1, 1, 1, NOW()),
(690000000000100202, '详情', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'merchant:merchant:get', 2, 1, 1, NOW()),
(690000000000100203, '新增', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'merchant:merchant:create', 3, 1, 1, NOW()),
(690000000000100204, '修改', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'merchant:merchant:update', 4, 1, 1, NOW()),
(690000000000100205, '启停', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'merchant:merchant:lifecycle', 5, 1, 1, NOW()),
(690000000000100206, '重新核验', 690000000000100200, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'merchant:merchant:reverify', 6, 1, 1, NOW())
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" BETWEEN 690000000000100200 AND 690000000000100206
WHERE role_data."code" = 'AGENT_ADMIN' AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (
    690000000000100000, 690000000000100200, 690000000000100201,
    690000000000100202, 690000000000100204
)
WHERE role_data."code" = 'MERCHANT_OPERATOR' AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (
    690000000000100000, 690000000000100200, 690000000000100201, 690000000000100202
)
WHERE role_data."code" = 'MERCHANT_REVIEWER' AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "tenant_package_menu" ("package_id", "menu_id")
SELECT package_data."id", menu_data."id"
FROM "tenant_package" package_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (690000000000100000, 690000000000100200)
WHERE package_data."deleted" = 0
ON CONFLICT ("package_id", "menu_id") DO NOTHING;
