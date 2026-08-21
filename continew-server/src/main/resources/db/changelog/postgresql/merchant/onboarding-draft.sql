-- liquibase formatted sql

-- changeset continew:merchant-phase1-onboarding-draft-postgresql
-- comment 进件草稿产品维度、活动草稿唯一保护和步骤完成状态
ALTER TABLE "biz_onboarding_application"
    ADD COLUMN "product_code" varchar(64) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN "active_draft_guard" varchar(32) DEFAULT NULL;
CREATE UNIQUE INDEX "uk_onboarding_active_draft"
    ON "biz_onboarding_application"
    ("tenant_id", "merchant_id", "channel_code", "product_code", "active_draft_guard", "deleted");

ALTER TABLE "biz_kyc_version"
    ADD COLUMN "step_completion_json" text NOT NULL DEFAULT '[]';
CREATE UNIQUE INDEX "uk_kyc_merchant_version"
    ON "biz_kyc_version" ("tenant_id", "merchant_id", "version_no", "deleted");
