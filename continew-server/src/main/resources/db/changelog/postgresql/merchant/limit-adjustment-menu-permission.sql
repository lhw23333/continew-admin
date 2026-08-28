-- liquibase formatted sql

-- changeset continew:merchant-phase1-limit-adjustment-menu-permission-postgresql
-- comment 商户限额调整创建与历史查询按钮权限
INSERT INTO "sys_menu"
("id", "title", "parent_id", "type", "permission", "sort", "status", "create_user", "create_time")
VALUES
(690000000000100208, '调整限额', 690000000000100200, 3, 'merchant:limit:create', 8, 1, 1, NOW()),
(690000000000100209, '限额历史', 690000000000100200, 3, 'merchant:limit:list', 9, 1, 1, NOW())
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (690000000000100208, 690000000000100209)
WHERE role_data."code" IN ('AGENT_ADMIN', 'MERCHANT_OPERATOR') AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", 690000000000100209, 0
FROM "sys_role" role_data
WHERE role_data."code" IN ('MERCHANT_REVIEWER', 'RISK_REVIEWER', 'CHANNEL_OPERATIONS')
  AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;