-- liquibase formatted sql

-- changeset continew:merchant-phase1-pricing-version-postgresql
CREATE TABLE IF NOT EXISTS "biz_agent_pricing_version" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "agent_id" int8 NOT NULL,
    "parent_pricing_version_id" int8 DEFAULT NULL, "version_no" int4 NOT NULL,
    "channel_code" varchar(64) NOT NULL, "product_code" varchar(64) NOT NULL,
    "currency" char(3) NOT NULL DEFAULT 'CNY', "pricing_rules_json" text NOT NULL,
    "effective_time" timestamp NOT NULL, "expires_time" timestamp DEFAULT NULL,
    "status" varchar(32) NOT NULL DEFAULT 'DRAFT',
    "create_user" int8 DEFAULT NULL, "create_time" timestamp NOT NULL,
    "update_user" int8 DEFAULT NULL, "update_time" timestamp DEFAULT NULL,
    "deleted" int8 NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_agent_pricing_version" IS '代理商定价版本表';

-- changeset continew:merchant-phase1-review-record-postgresql
CREATE TABLE IF NOT EXISTS "biz_review_record" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "business_type" varchar(64) NOT NULL,
    "business_id" int8 NOT NULL, "business_version" int8 NOT NULL,
    "process_instance_id" varchar(64) DEFAULT NULL, "task_id" varchar(64) DEFAULT NULL,
    "review_type" varchar(32) NOT NULL, "reviewer_id" varchar(64) DEFAULT NULL,
    "action" varchar(32) NOT NULL, "opinion" varchar(2000) DEFAULT NULL,
    "issue_codes_json" text DEFAULT NULL, "decision_payload_json" text DEFAULT NULL,
    "model_version" varchar(128) DEFAULT NULL, "evidence_summary" varchar(2000) DEFAULT NULL,
    "decision_time" timestamp NOT NULL, "create_user" int8 DEFAULT NULL,
    "create_time" timestamp NOT NULL, PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_review_record" IS '审核决策记录表';

-- changeset continew:merchant-phase1-workflow-mapping-postgresql
CREATE TABLE IF NOT EXISTS "biz_workflow_instance" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "business_type" varchar(64) NOT NULL,
    "business_id" int8 NOT NULL, "business_version" int8 NOT NULL,
    "process_definition_key" varchar(128) NOT NULL, "process_definition_version" int4 NOT NULL,
    "process_instance_id" varchar(64) NOT NULL, "business_key" varchar(255) NOT NULL,
    "workflow_status" varchar(32) NOT NULL DEFAULT 'RUNNING',
    "started_time" timestamp NOT NULL, "ended_time" timestamp DEFAULT NULL,
    "row_version" int8 NOT NULL DEFAULT 0, "create_user" int8 DEFAULT NULL,
    "create_time" timestamp NOT NULL, "update_user" int8 DEFAULT NULL,
    "update_time" timestamp DEFAULT NULL, "deleted" int8 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_workflow_instance" IS '业务流程映射表';

-- changeset continew:merchant-phase1-limit-adjustment-postgresql
CREATE TABLE IF NOT EXISTS "biz_limit_adjustment" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "request_no" varchar(64) NOT NULL,
    "merchant_id" int8 NOT NULL, "owning_agent_id" int8 NOT NULL,
    "channel_code" varchar(64) NOT NULL, "platform_code" varchar(64) NOT NULL,
    "currency" char(3) NOT NULL DEFAULT 'CNY', "original_limit" numeric(20,2) NOT NULL,
    "requested_limit" numeric(20,2) NOT NULL, "normalized_limit" numeric(20,2) NOT NULL,
    "effective_limit" numeric(20,2) DEFAULT NULL, "reason" varchar(1000) NOT NULL,
    "eligibility_version" varchar(64) NOT NULL, "channel_config_version" varchar(64) NOT NULL,
    "process_instance_id" varchar(64) DEFAULT NULL,
    "approval_status" varchar(32) NOT NULL DEFAULT 'PENDING',
    "channel_status" varchar(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    "effective_status" varchar(32) NOT NULL DEFAULT 'NOT_EFFECTIVE',
    "applicant_id" int8 NOT NULL, "application_time" timestamp NOT NULL,
    "approval_time" timestamp DEFAULT NULL, "effective_time" timestamp DEFAULT NULL,
    "opinion" varchar(2000) DEFAULT NULL, "channel_result_code" varchar(128) DEFAULT NULL,
    "channel_result_message" varchar(1000) DEFAULT NULL, "row_version" int8 NOT NULL DEFAULT 0,
    "create_user" int8 DEFAULT NULL, "create_time" timestamp NOT NULL,
    "update_user" int8 DEFAULT NULL, "update_time" timestamp DEFAULT NULL,
    "deleted" int8 NOT NULL DEFAULT 0, PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_limit_adjustment" IS '商户限额调整申请表';

-- changeset continew:merchant-phase1-outbox-postgresql
CREATE TABLE IF NOT EXISTS "biz_outbox_event" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "aggregate_type" varchar(64) NOT NULL,
    "aggregate_id" int8 NOT NULL, "aggregate_version" int8 NOT NULL,
    "event_type" varchar(128) NOT NULL, "event_key" varchar(255) NOT NULL,
    "payload_json" text NOT NULL, "headers_json" text DEFAULT NULL,
    "status" varchar(32) NOT NULL DEFAULT 'PENDING', "retry_count" int4 NOT NULL DEFAULT 0,
    "next_retry_time" timestamp DEFAULT NULL, "locked_by" varchar(128) DEFAULT NULL,
    "locked_time" timestamp DEFAULT NULL, "occurred_time" timestamp NOT NULL,
    "published_time" timestamp DEFAULT NULL, "last_error_category" varchar(64) DEFAULT NULL,
    "last_error_message" varchar(1000) DEFAULT NULL, "trace_id" varchar(64) DEFAULT NULL,
    "create_time" timestamp NOT NULL, "update_time" timestamp DEFAULT NULL, PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_outbox_event" IS '事务型Outbox事件表';

-- changeset continew:merchant-phase1-channel-event-postgresql
CREATE TABLE IF NOT EXISTS "biz_channel_event" (
    "id" int8 NOT NULL, "tenant_id" int8 NOT NULL, "channel_code" varchar(64) NOT NULL,
    "channel_event_id" varchar(128) DEFAULT NULL, "event_key" varchar(255) NOT NULL,
    "application_id" int8 DEFAULT NULL, "merchant_id" int8 NOT NULL,
    "business_serial" varchar(128) NOT NULL, "event_type" varchar(64) NOT NULL,
    "raw_status" varchar(128) DEFAULT NULL, "normalized_state_type" varchar(64) DEFAULT NULL,
    "normalized_status" varchar(32) DEFAULT NULL, "mapping_version" varchar(64) NOT NULL,
    "payload_hash" char(64) NOT NULL, "sanitized_payload_json" text DEFAULT NULL,
    "signature_key_version" varchar(64) DEFAULT NULL, "occurred_time" timestamp DEFAULT NULL,
    "received_time" timestamp NOT NULL, "processed_time" timestamp DEFAULT NULL,
    "processing_status" varchar(32) NOT NULL DEFAULT 'RECEIVED',
    "retry_count" int4 NOT NULL DEFAULT 0, "last_error_category" varchar(64) DEFAULT NULL,
    "last_error_message" varchar(1000) DEFAULT NULL, "trace_id" varchar(64) DEFAULT NULL,
    "row_version" int8 NOT NULL DEFAULT 0, "create_time" timestamp NOT NULL,
    "update_time" timestamp DEFAULT NULL, PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_channel_event" IS '渠道事件表';
