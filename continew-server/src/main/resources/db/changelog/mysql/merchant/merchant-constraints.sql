-- liquibase formatted sql

-- changeset continew:merchant-phase1-unique-constraints-mysql
-- comment 商户一期业务唯一性与幂等约束
CREATE UNIQUE INDEX `uk_merchant_legal_subject_hash`
    ON `biz_merchant` (`tenant_id`, `legal_subject_hash`, `deleted`);

ALTER TABLE `biz_onboarding_application`
    ADD COLUMN `active_idempotency_guard` tinyint
    GENERATED ALWAYS AS (
        CASE
            WHEN `deleted` = 0
             AND `status` IN ('SUBMITTED', 'UNDER_REVIEW', 'SUPPLEMENT_REQUIRED', 'APPROVED', 'CHANNEL_PROCESSING')
            THEN 1
            ELSE NULL
        END
    ) STORED COMMENT '活动进件唯一性守卫';

CREATE UNIQUE INDEX `uk_onboarding_active_idempotency`
    ON `biz_onboarding_application`
    (`tenant_id`, `merchant_id`, `channel_code`, `kyc_version_id`, `idempotency_key`, `active_idempotency_guard`);

CREATE UNIQUE INDEX `uk_workflow_business_key`
    ON `biz_workflow_instance` (`tenant_id`, `business_key`, `deleted`);

CREATE UNIQUE INDEX `uk_outbox_event_key`
    ON `biz_outbox_event` (`tenant_id`, `event_key`);

CREATE UNIQUE INDEX `uk_channel_event_key`
    ON `biz_channel_event` (`tenant_id`, `channel_code`, `event_key`);
