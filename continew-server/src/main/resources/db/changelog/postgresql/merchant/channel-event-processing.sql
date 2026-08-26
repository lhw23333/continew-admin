-- liquibase formatted sql

-- changeset continew:merchant-phase1-channel-event-application-state-postgresql
ALTER TABLE "biz_onboarding_application"
    ADD COLUMN "channel_business_serial" varchar(128) DEFAULT NULL,
    ADD COLUMN "reporting_rank" int4 NOT NULL DEFAULT 0,
    ADD COLUMN "agreement_rank" int4 NOT NULL DEFAULT 0,
    ADD COLUMN "card_binding_rank" int4 NOT NULL DEFAULT 0,
    ADD COLUMN "reserve_account_rank" int4 NOT NULL DEFAULT 0,
    ADD COLUMN "channel_final_rank" int4 NOT NULL DEFAULT 0,
    ADD COLUMN "channel_final_terminal" boolean NOT NULL DEFAULT false;

CREATE INDEX "idx_onboarding_channel_serial"
    ON "biz_onboarding_application" ("tenant_id", "channel_code", "channel_business_serial", "id");

-- changeset continew:merchant-phase1-channel-event-normalized-snapshot-postgresql
ALTER TABLE "biz_channel_event"
    ADD COLUMN "product_code" varchar(64) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN "config_version" varchar(64) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN "business_type" varchar(64) NOT NULL DEFAULT 'ONBOARDING',
    ADD COLUMN "business_version" int8 NOT NULL DEFAULT 1,
    ADD COLUMN "channel_request_id" varchar(191) DEFAULT NULL,
    ADD COLUMN "operation_status" varchar(32) DEFAULT NULL,
    ADD COLUMN "reporting_status" varchar(32) DEFAULT NULL,
    ADD COLUMN "signing_status" varchar(32) DEFAULT NULL,
    ADD COLUMN "card_binding_status" varchar(32) DEFAULT NULL,
    ADD COLUMN "reserve_account_status" varchar(32) DEFAULT NULL,
    ADD COLUMN "final_status" varchar(32) DEFAULT NULL,
    ADD COLUMN "progression_rank" int4 DEFAULT NULL,
    ADD COLUMN "state_applied" boolean NOT NULL DEFAULT false;

CREATE INDEX "idx_channel_event_business_serial"
    ON "biz_channel_event"
        ("tenant_id", "channel_code", "product_code", "business_serial", "received_time", "id");
