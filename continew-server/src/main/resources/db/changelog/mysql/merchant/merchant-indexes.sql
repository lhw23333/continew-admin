-- liquibase formatted sql

-- changeset continew:merchant-phase1-query-indexes-mysql
-- comment 商户一期范围、状态、时间和恢复队列复合索引
CREATE INDEX `idx_agent_scope_status_time`
    ON `biz_agent` (`tenant_id`, `parent_id`, `status`, `deleted`, `create_time`, `id`);
CREATE INDEX `idx_agent_closure_descendant`
    ON `biz_agent_closure` (`tenant_id`, `descendant_id`, `ancestor_id`, `depth`);
CREATE INDEX `idx_merchant_scope_status_time`
    ON `biz_merchant` (`tenant_id`, `owning_agent_id`, `status`, `deleted`, `create_time`, `id`);
CREATE INDEX `idx_merchant_type_status_time`
    ON `biz_merchant` (`tenant_id`, `merchant_type`, `status`, `deleted`, `create_time`, `id`);
CREATE INDEX `idx_onboarding_scope_status_time`
    ON `biz_onboarding_application` (`tenant_id`, `owning_agent_id`, `status`, `submitted_time`, `id`);
CREATE INDEX `idx_onboarding_merchant_channel_status`
    ON `biz_onboarding_application` (`tenant_id`, `merchant_id`, `channel_code`, `status`, `create_time`, `id`);
CREATE INDEX `idx_kyc_merchant_status_version`
    ON `biz_kyc_version` (`tenant_id`, `merchant_id`, `status`, `version_no`, `id`);
CREATE INDEX `idx_attachment_version_type`
    ON `biz_kyc_attachment` (`tenant_id`, `kyc_version_id`, `evidence_type`, `deleted`, `id`);
CREATE INDEX `idx_pricing_agent_effective`
    ON `biz_agent_pricing_version` (`tenant_id`, `agent_id`, `channel_code`, `product_code`, `status`, `effective_time`, `id`);
CREATE INDEX `idx_review_business_time`
    ON `biz_review_record` (`tenant_id`, `business_type`, `business_id`, `business_version`, `decision_time`, `id`);
CREATE INDEX `idx_workflow_status_time`
    ON `biz_workflow_instance` (`tenant_id`, `workflow_status`, `started_time`, `id`);
CREATE INDEX `idx_limit_scope_status_time`
    ON `biz_limit_adjustment` (`tenant_id`, `owning_agent_id`, `approval_status`, `application_time`, `id`);
CREATE INDEX `idx_outbox_status_retry`
    ON `biz_outbox_event` (`status`, `next_retry_time`, `id`);
CREATE INDEX `idx_outbox_tenant_time`
    ON `biz_outbox_event` (`tenant_id`, `status`, `occurred_time`, `id`);
CREATE INDEX `idx_channel_event_status_time`
    ON `biz_channel_event` (`tenant_id`, `channel_code`, `processing_status`, `received_time`, `id`);
CREATE INDEX `idx_channel_event_application_time`
    ON `biz_channel_event` (`tenant_id`, `application_id`, `received_time`, `id`);
