-- liquibase formatted sql
-- changeset continew:merchant-phase1-workflow-deployment-mysql
CREATE TABLE IF NOT EXISTS `biz_workflow_deployment` (
 `id` bigint(20) NOT NULL, `tenant_id` bigint(20) NOT NULL, `deployment_id` varchar(64) NOT NULL,
 `process_definition_id` varchar(128) NOT NULL, `process_definition_key` varchar(128) NOT NULL,
 `process_definition_version` int NOT NULL, `contract_version` int NOT NULL,
 `resource_name` varchar(255) NOT NULL, `resource_sha256` char(64) NOT NULL,
 `deployed_by` bigint(20) NOT NULL, `deployed_time` datetime(3) NOT NULL,
 PRIMARY KEY (`id`), UNIQUE INDEX `uk_workflow_contract_version` (`tenant_id`,`process_definition_key`,`contract_version`),
 UNIQUE INDEX `uk_workflow_resource_hash` (`tenant_id`,`process_definition_key`,`resource_sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变BPMN部署元数据';
-- changeset continew:merchant-phase1-workflow-deployment-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_workflow_deployment_no_update` BEFORE UPDATE ON `biz_workflow_deployment` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_workflow_deployment is append-only';
-- changeset continew:merchant-phase1-workflow-deployment-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_workflow_deployment_no_delete` BEFORE DELETE ON `biz_workflow_deployment` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_workflow_deployment is append-only';
