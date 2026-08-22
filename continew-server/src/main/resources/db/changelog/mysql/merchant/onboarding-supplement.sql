-- liquibase formatted sql

-- changeset continew:merchant-phase1-onboarding-supplement-mysql
ALTER TABLE `biz_kyc_version`
    ADD COLUMN `supplement_task_id` varchar(64) DEFAULT NULL COMMENT '创建该补件版本的Flowable任务ID'
        AFTER `previous_version_id`;
CREATE UNIQUE INDEX `uk_kyc_supplement_task`
    ON `biz_kyc_version` (`tenant_id`, `onboarding_application_id`, `supplement_task_id`, `deleted`);
