-- liquibase formatted sql
-- changeset continew:merchant-phase1-workflow-notification-postgresql
CREATE TABLE IF NOT EXISTS "biz_workflow_notification" (
 "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "notification_key" varchar(191) NOT NULL,
 "event_type" varchar(32) NOT NULL, "recipient_user_id" int8 NOT NULL,
 "process_instance_id" varchar(64) NOT NULL, "task_id" varchar(64) DEFAULT NULL,
 "title" varchar(50) NOT NULL, "content" varchar(255) NOT NULL, "path" varchar(255) NOT NULL,
 "status" varchar(16) NOT NULL DEFAULT 'PENDING', "message_id" int8 DEFAULT NULL,
 "create_time" timestamp NOT NULL, "sent_time" timestamp DEFAULT NULL, PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_workflow_notification_key" ON "biz_workflow_notification" ("tenant_id","notification_key");
CREATE INDEX "idx_workflow_notification_pending" ON "biz_workflow_notification" ("status","create_time","id");
