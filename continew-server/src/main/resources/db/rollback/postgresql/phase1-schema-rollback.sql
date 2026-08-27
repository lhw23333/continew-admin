-- Phase-one schema rollback for empty test databases only. Never run for application rollback.
DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE code = 'AGENT_ADMIN');
DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE code = 'AGENT_ADMIN');
DELETE FROM tenant_package_menu WHERE menu_id IN (690000000000100000, 690000000000100100, 690000000000100200);
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 690000000000100200 AND 690000000000100207;
DELETE FROM sys_menu WHERE id BETWEEN 690000000000100200 AND 690000000000100207;
DELETE FROM sys_menu WHERE id BETWEEN 690000000000100000 AND 690000000000100109;
DELETE FROM sys_role WHERE code = 'AGENT_ADMIN';
DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE code IN ('MERCHANT_OPERATOR', 'MERCHANT_REVIEWER'));
DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE code IN ('MERCHANT_OPERATOR', 'MERCHANT_REVIEWER'));
DELETE FROM sys_role WHERE code IN ('MERCHANT_OPERATOR', 'MERCHANT_REVIEWER');
ALTER TABLE sys_user DROP COLUMN IF EXISTS must_change_password;
DROP TABLE IF EXISTS biz_channel_recovery CASCADE;
DROP TABLE IF EXISTS biz_channel_callback_security_audit CASCADE;
DROP FUNCTION IF EXISTS biz_prevent_channel_callback_security_audit_mutation() CASCADE;
DROP TABLE IF EXISTS biz_channel_callback_nonce CASCADE;
DROP TABLE IF EXISTS biz_channel_transport_audit CASCADE;
DROP FUNCTION IF EXISTS biz_prevent_channel_transport_audit_mutation() CASCADE;
DROP TABLE IF EXISTS biz_security_audit CASCADE;
DROP TABLE IF EXISTS biz_kyc_platform_attachment CASCADE;
DROP TABLE IF EXISTS biz_kyc_operating_platform CASCADE;
DROP FUNCTION IF EXISTS biz_prevent_security_audit_mutation() CASCADE;
DROP TABLE IF EXISTS biz_channel_event CASCADE;
DROP TABLE IF EXISTS biz_channel_product_version CASCADE;
DROP FUNCTION IF EXISTS biz_prevent_channel_product_mutation() CASCADE;
DROP TABLE IF EXISTS biz_outbox_event CASCADE;
DROP TABLE IF EXISTS biz_limit_adjustment CASCADE;
DROP TABLE IF EXISTS biz_workflow_instance CASCADE;
DROP TABLE IF EXISTS biz_review_record CASCADE;
DROP TABLE IF EXISTS biz_merchant_reverification_request CASCADE;
DROP TABLE IF EXISTS biz_kyc_draft_default_snapshot CASCADE;
DROP TABLE IF EXISTS biz_agent_merchant_default_version CASCADE;
DROP FUNCTION IF EXISTS biz_prevent_agent_default_mutation() CASCADE;
DROP TABLE IF EXISTS biz_agent_pricing_version CASCADE;
DROP FUNCTION IF EXISTS biz_prevent_agent_pricing_mutation() CASCADE;
DROP TABLE IF EXISTS biz_kyc_attachment CASCADE;
DROP TABLE IF EXISTS biz_kyc_version CASCADE;
DROP TABLE IF EXISTS biz_onboarding_application CASCADE;
DROP TABLE IF EXISTS biz_merchant CASCADE;
DROP TABLE IF EXISTS biz_agent_closure CASCADE;
DROP TABLE IF EXISTS biz_agent CASCADE;

-- Flowable 7.1.0.2 official rollback: engine -> history -> common.
drop table if exists ACT_RU_ACTINST cascade;
drop table if exists ACT_RE_DEPLOYMENT cascade;
drop table if exists ACT_RE_MODEL cascade;
drop table if exists ACT_RE_PROCDEF cascade;
drop table if exists ACT_RU_EXECUTION cascade;
drop table if exists ACT_EVT_LOG cascade;
drop table if exists ACT_PROCDEF_INFO cascade;

drop table if exists ACT_HI_PROCINST cascade;
drop table if exists ACT_HI_ACTINST cascade;
drop table if exists ACT_HI_DETAIL cascade;
drop table if exists ACT_HI_COMMENT cascade;
drop table if exists ACT_HI_ATTACHMENT cascade;


drop table if exists FLW_RU_BATCH_PART cascade;
drop table if exists FLW_RU_BATCH cascade;

drop table if exists ACT_RU_ENTITYLINK cascade;
drop table if exists ACT_HI_ENTITYLINK cascade;

drop table if exists ACT_RU_EVENT_SUBSCR cascade;

drop table if exists ACT_RU_IDENTITYLINK cascade;
drop table if exists ACT_HI_IDENTITYLINK cascade;

drop table if exists ACT_RU_JOB cascade;
drop table if exists ACT_RU_TIMER_JOB cascade;
drop table if exists ACT_RU_SUSPENDED_JOB cascade;
drop table if exists ACT_RU_DEADLETTER_JOB cascade;
drop table if exists ACT_RU_HISTORY_JOB cascade;
drop table if exists ACT_RU_EXTERNAL_JOB cascade;

drop table if exists ACT_RU_TASK cascade;
drop table if exists ACT_HI_TSK_LOG cascade;
drop table if exists ACT_HI_TASKINST cascade;


drop table if exists ACT_RU_VARIABLE cascade;
drop table if exists ACT_HI_VARINST cascade;


drop table if exists ACT_GE_BYTEARRAY cascade;
drop table if exists ACT_GE_PROPERTY cascade;
