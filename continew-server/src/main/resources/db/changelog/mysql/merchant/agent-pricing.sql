-- liquibase formatted sql

-- changeset continew:merchant-phase1-agent-pricing-uniqueness-mysql
-- comment 代理商定价版本号和生效时点租户内唯一
CREATE UNIQUE INDEX `uk_agent_pricing_version`
    ON `biz_agent_pricing_version`
    (`tenant_id`, `agent_id`, `channel_code`, `product_code`, `currency`, `version_no`, `deleted`);
CREATE UNIQUE INDEX `uk_agent_pricing_effective_time`
    ON `biz_agent_pricing_version`
    (`tenant_id`, `agent_id`, `channel_code`, `product_code`, `currency`, `effective_time`, `deleted`);

-- changeset continew:merchant-phase1-agent-pricing-no-update-mysql splitStatements:false
CREATE TRIGGER `trg_agent_pricing_no_update`
    BEFORE UPDATE ON `biz_agent_pricing_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_agent_pricing_version is append-only';

-- changeset continew:merchant-phase1-agent-pricing-no-delete-mysql splitStatements:false
CREATE TRIGGER `trg_agent_pricing_no_delete`
    BEFORE DELETE ON `biz_agent_pricing_version`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'biz_agent_pricing_version is append-only';
