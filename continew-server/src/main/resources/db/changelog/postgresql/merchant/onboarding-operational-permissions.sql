-- liquibase formatted sql

-- changeset continew:merchant-phase1-onboarding-operational-permissions-postgresql
-- comment 商户进件草稿与KYC附件操作权限
INSERT INTO "sys_menu"
("id", "title", "parent_id", "type", "permission", "sort", "status", "create_user", "create_time")
VALUES
(690000000000100210, '进件草稿', 690000000000100200, 3, 'merchant:onboarding:draft', 10, 1, 1, NOW()),
(690000000000100211, '上传KYC附件', 690000000000100200, 3, 'merchant:kyc:attachment:upload', 11, 1, 1, NOW()),
(690000000000100212, '查看KYC附件', 690000000000100200, 3, 'merchant:kyc:attachment:view', 12, 1, 1, NOW())
ON CONFLICT ("id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (690000000000100210, 690000000000100211, 690000000000100212)
WHERE role_data."code" = 'AGENT_ADMIN' AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", menu_data."id", 0
FROM "sys_role" role_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (690000000000100210, 690000000000100211)
WHERE role_data."code" = 'MERCHANT_OPERATOR' AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "sys_role_menu" ("role_id", "menu_id", "tenant_id")
SELECT role_data."id", 690000000000100212, 0
FROM "sys_role" role_data
WHERE role_data."code" IN ('MERCHANT_REVIEWER', 'RISK_REVIEWER') AND role_data."deleted" = 0
ON CONFLICT ("role_id", "menu_id") DO NOTHING;

INSERT INTO "tenant_package_menu" ("package_id", "menu_id")
SELECT package_data."id", menu_data."id"
FROM "tenant_package" package_data
JOIN "sys_menu" menu_data ON menu_data."id" IN (690000000000100200, 690000000000100210, 690000000000100211, 690000000000100212)
WHERE package_data."deleted" = 0
ON CONFLICT ("package_id", "menu_id") DO NOTHING;