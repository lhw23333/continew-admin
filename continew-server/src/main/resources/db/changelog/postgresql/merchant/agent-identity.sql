-- liquibase formatted sql

-- changeset continew:merchant-phase1-user-must-change-password-postgresql
ALTER TABLE "sys_user"
    ADD COLUMN "must_change_password" bool NOT NULL DEFAULT false;

-- changeset continew:merchant-phase1-agent-department-postgresql
ALTER TABLE "biz_agent"
    ADD COLUMN "dept_id" int8 DEFAULT NULL,
    ADD COLUMN "remarks" varchar(255) DEFAULT NULL;
CREATE UNIQUE INDEX "uk_agent_department"
    ON "biz_agent" ("tenant_id", "dept_id", "deleted");

-- changeset continew:merchant-phase1-agent-admin-role-postgresql
INSERT INTO "sys_role"
("id", "name", "code", "data_scope", "description", "sort", "is_system", "menu_check_strictly",
 "dept_check_strictly", "create_user", "create_time", "deleted")
SELECT 690000000000000001, '代理商管理员', 'AGENT_ADMIN', 4,
       '商户一期代理商管理员，业务范围由biz_agent_closure控制', 50, true, true, true, 1, NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM "sys_role" WHERE "code" = 'AGENT_ADMIN' AND "deleted" = 0);
