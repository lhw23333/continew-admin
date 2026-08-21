-- liquibase formatted sql

-- changeset continew:merchant-phase1-merchant-reviewer-mobile-postgresql
-- comment 商户复核手机号加密字段与商户身份角色
ALTER TABLE "biz_merchant"
    ADD COLUMN "reviewer_mobile_ciphertext" bytea DEFAULT NULL,
    ADD COLUMN "reviewer_mobile_hash" char(64) DEFAULT NULL,
    ADD COLUMN "reviewer_mobile_hash_key_version" varchar(32) DEFAULT NULL,
    ADD COLUMN "reviewer_mobile_masked" varchar(32) DEFAULT NULL,
    ADD COLUMN "reviewer_mobile_key_version" varchar(32) DEFAULT NULL;

INSERT INTO "sys_role"
("id", "name", "code", "data_scope", "description", "sort", "is_system", "menu_check_strictly",
 "dept_check_strictly", "create_user", "create_time", "deleted")
SELECT 690000000000000002, '商户操作员', 'MERCHANT_OPERATOR', 4,
       '商户一期操作员，商户范围由业务授权服务控制', 51, true, true, true, 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM "sys_role" WHERE "code" = 'MERCHANT_OPERATOR' AND "deleted" = 0);

INSERT INTO "sys_role"
("id", "name", "code", "data_scope", "description", "sort", "is_system", "menu_check_strictly",
 "dept_check_strictly", "create_user", "create_time", "deleted")
SELECT 690000000000000003, '商户复核员', 'MERCHANT_REVIEWER', 4,
       '商户一期复核员，商户范围由业务授权服务控制', 52, true, true, true, 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM "sys_role" WHERE "code" = 'MERCHANT_REVIEWER' AND "deleted" = 0);
