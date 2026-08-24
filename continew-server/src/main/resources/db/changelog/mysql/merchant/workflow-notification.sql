-- liquibase formatted sql
-- changeset continew:merchant-phase1-workflow-notification-mysql
CREATE TABLE IF NOT EXISTS `biz_workflow_notification` (
 `id` bigint(20) NOT NULL, `tenant_id` bigint(20) NOT NULL, `notification_key` varchar(191) NOT NULL,
 `event_type` varchar(32) NOT NULL, `recipient_user_id` bigint(20) NOT NULL,
 `process_instance_id` varchar(64) NOT NULL, `task_id` varchar(64) DEFAULT NULL,
 `title` varchar(50) NOT NULL, `content` varchar(255) NOT NULL, `path` varchar(255) NOT NULL,
 `status` varchar(16) NOT NULL DEFAULT 'PENDING', `message_id` bigint(20) DEFAULT NULL,
 `create_time` datetime(3) NOT NULL, `sent_time` datetime(3) DEFAULT NULL,
 PRIMARY KEY (`id`), UNIQUE INDEX `uk_workflow_notification_key` (`tenant_id`,`notification_key`),
 INDEX `idx_workflow_notification_pending` (`status`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流站内通知幂等队列';
