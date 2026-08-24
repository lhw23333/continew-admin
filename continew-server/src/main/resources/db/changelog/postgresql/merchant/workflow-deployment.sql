-- liquibase formatted sql
-- changeset continew:merchant-phase1-workflow-deployment-postgresql
CREATE TABLE IF NOT EXISTS "biz_workflow_deployment" (
 "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "deployment_id" varchar(64) NOT NULL,
 "process_definition_id" varchar(128) NOT NULL, "process_definition_key" varchar(128) NOT NULL,
 "process_definition_version" int4 NOT NULL, "contract_version" int4 NOT NULL,
 "resource_name" varchar(255) NOT NULL, "resource_sha256" char(64) NOT NULL,
 "deployed_by" int8 NOT NULL, "deployed_time" timestamp NOT NULL, PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_workflow_contract_version" ON "biz_workflow_deployment" ("tenant_id","process_definition_key","contract_version");
CREATE UNIQUE INDEX "uk_workflow_resource_hash" ON "biz_workflow_deployment" ("tenant_id","process_definition_key","resource_sha256");
-- changeset continew:merchant-phase1-workflow-deployment-function-postgresql splitStatements:false
CREATE OR REPLACE FUNCTION biz_prevent_workflow_deployment_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'biz_workflow_deployment is append-only'; END; $$ LANGUAGE plpgsql;
-- changeset continew:merchant-phase1-workflow-deployment-trigger-postgresql
CREATE TRIGGER trg_workflow_deployment_immutable BEFORE UPDATE OR DELETE ON "biz_workflow_deployment" FOR EACH ROW EXECUTE FUNCTION biz_prevent_workflow_deployment_mutation();
