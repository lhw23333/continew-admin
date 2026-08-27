-- liquibase formatted sql

--changeset continew:merchant-phase1-limit-adjustment-immutable-fields-mysql splitStatements:false
CREATE TRIGGER `trg_limit_adjustment_immutable_fields`
    BEFORE UPDATE ON `biz_limit_adjustment`
    FOR EACH ROW
BEGIN
    IF NOT (OLD.`tenant_id` <=> NEW.`tenant_id`)
       OR NOT (OLD.`request_no` <=> NEW.`request_no`)
       OR NOT (OLD.`merchant_id` <=> NEW.`merchant_id`)
       OR NOT (OLD.`owning_agent_id` <=> NEW.`owning_agent_id`)
       OR NOT (OLD.`channel_code` <=> NEW.`channel_code`)
       OR NOT (OLD.`platform_code` <=> NEW.`platform_code`)
       OR NOT (OLD.`currency` <=> NEW.`currency`)
       OR NOT (OLD.`original_limit` <=> NEW.`original_limit`)
       OR NOT (OLD.`requested_limit` <=> NEW.`requested_limit`)
       OR NOT (OLD.`normalized_limit` <=> NEW.`normalized_limit`)
       OR NOT (OLD.`reason` <=> NEW.`reason`)
       OR NOT (OLD.`eligibility_version` <=> NEW.`eligibility_version`)
       OR NOT (OLD.`channel_config_version` <=> NEW.`channel_config_version`)
       OR NOT (OLD.`amount_policy_version` <=> NEW.`amount_policy_version`)
       OR NOT (OLD.`applicant_id` <=> NEW.`applicant_id`)
       OR NOT (OLD.`application_time` <=> NEW.`application_time`)
       OR NOT (OLD.`create_time` <=> NEW.`create_time`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'limit adjustment evidence is immutable';
    END IF;
END;
