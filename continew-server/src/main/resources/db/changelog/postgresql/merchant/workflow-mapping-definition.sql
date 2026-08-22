-- liquibase formatted sql

-- changeset continew:merchant-phase1-workflow-mapping-definition-postgresql
-- comment 保存实际Flowable流程定义ID，旧映射明确标记为UNMAPPED
ALTER TABLE "biz_workflow_instance"
    ADD COLUMN "process_definition_id" varchar(128) NOT NULL DEFAULT 'UNMAPPED';
