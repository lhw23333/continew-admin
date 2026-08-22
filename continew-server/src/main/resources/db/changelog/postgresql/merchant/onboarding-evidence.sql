-- liquibase formatted sql

-- changeset continew:merchant-phase1-onboarding-evidence-postgresql
-- comment 草稿创建时快照渠道材料要求，避免后续配置变化改写历史
ALTER TABLE "biz_onboarding_application"
    ADD COLUMN "requirement_summary_json" text DEFAULT NULL;
