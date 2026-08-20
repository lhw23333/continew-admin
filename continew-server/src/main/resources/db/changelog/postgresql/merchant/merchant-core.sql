-- liquibase formatted sql

-- changeset continew:merchant-phase1-agent-core-postgresql
-- comment 代理商主表与闭包路径表
CREATE TABLE IF NOT EXISTS "biz_agent" (
    "id"                         int8          NOT NULL,
    "tenant_id"                  int8          NOT NULL,
    "parent_id"                  int8          NOT NULL DEFAULT 0,
    "path"                       varchar(1024) NOT NULL DEFAULT '',
    "user_id"                    int8          NOT NULL,
    "agent_no"                   varchar(64)   NOT NULL,
    "name"                       varchar(100)  NOT NULL,
    "contact_name"               varchar(100)  DEFAULT NULL,
    "contact_mobile_ciphertext"  bytea         DEFAULT NULL,
    "contact_mobile_hash"        char(64)      DEFAULT NULL,
    "contact_mobile_masked"      varchar(32)   DEFAULT NULL,
    "contact_mobile_key_version" varchar(32)   DEFAULT NULL,
    "promotion_code"             varchar(64)   DEFAULT NULL,
    "status"                     varchar(32)   NOT NULL DEFAULT 'ENABLED',
    "disabled_reason"            varchar(255)  DEFAULT NULL,
    "row_version"                int8          NOT NULL DEFAULT 0,
    "create_user"                int8          DEFAULT NULL,
    "create_time"                timestamp     NOT NULL,
    "update_user"                int8          DEFAULT NULL,
    "update_time"                timestamp     DEFAULT NULL,
    "deleted"                    int8          NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_agent" IS '代理商表';

CREATE TABLE IF NOT EXISTS "biz_agent_closure" (
    "tenant_id"     int8      NOT NULL,
    "ancestor_id"   int8      NOT NULL,
    "descendant_id" int8      NOT NULL,
    "depth"         int4      NOT NULL,
    "create_time"   timestamp NOT NULL,
    PRIMARY KEY ("tenant_id", "ancestor_id", "descendant_id")
);
COMMENT ON TABLE "biz_agent_closure" IS '代理商闭包路径表';

-- changeset continew:merchant-phase1-merchant-master-postgresql
-- comment 商户主表
CREATE TABLE IF NOT EXISTS "biz_merchant" (
    "id"                         int8         NOT NULL,
    "tenant_id"                  int8         NOT NULL,
    "owning_agent_id"            int8         NOT NULL,
    "merchant_no"                varchar(64)  NOT NULL,
    "merchant_type"              varchar(32)  NOT NULL,
    "legal_name"                 varchar(200) NOT NULL,
    "short_name"                 varchar(100) NOT NULL,
    "legal_subject_hash"         char(64)     DEFAULT NULL,
    "operator_user_id"           int8         NOT NULL,
    "reviewer_user_id"           int8         NOT NULL,
    "contact_name"               varchar(100) DEFAULT NULL,
    "contact_mobile_ciphertext"  bytea        DEFAULT NULL,
    "contact_mobile_hash"        char(64)     DEFAULT NULL,
    "contact_mobile_masked"      varchar(32)  DEFAULT NULL,
    "contact_mobile_key_version" varchar(32)  DEFAULT NULL,
    "industry"                   varchar(100) DEFAULT NULL,
    "product_description"        varchar(255) DEFAULT NULL,
    "status"                     varchar(32)  NOT NULL DEFAULT 'DRAFT',
    "disabled_reason"            varchar(255) DEFAULT NULL,
    "certified_kyc_version_id"   int8         DEFAULT NULL,
    "row_version"                int8         NOT NULL DEFAULT 0,
    "create_user"                int8         DEFAULT NULL,
    "create_time"                timestamp    NOT NULL,
    "update_user"                int8         DEFAULT NULL,
    "update_time"                timestamp    DEFAULT NULL,
    "deleted"                    int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_merchant" IS '商户主表';

-- changeset continew:merchant-phase1-onboarding-application-postgresql
-- comment 商户渠道进件申请表
CREATE TABLE IF NOT EXISTS "biz_onboarding_application" (
    "id"                     int8         NOT NULL,
    "tenant_id"              int8         NOT NULL,
    "application_no"         varchar(64)  NOT NULL,
    "merchant_id"            int8         NOT NULL,
    "owning_agent_id"        int8         NOT NULL,
    "channel_code"           varchar(64)  NOT NULL,
    "requirement_version"    varchar(64)  NOT NULL,
    "channel_config_version" varchar(64)  DEFAULT NULL,
    "kyc_version_id"         int8         DEFAULT NULL,
    "idempotency_key"        varchar(128) DEFAULT NULL,
    "status"                 varchar(32)  NOT NULL DEFAULT 'DRAFT',
    "reporting_status"       varchar(32)  NOT NULL DEFAULT 'NOT_STARTED',
    "agreement_status"       varchar(32)  NOT NULL DEFAULT 'NOT_STARTED',
    "card_binding_status"    varchar(32)  NOT NULL DEFAULT 'NOT_STARTED',
    "reserve_account_status" varchar(32)  NOT NULL DEFAULT 'NOT_STARTED',
    "channel_final_status"   varchar(32)  NOT NULL DEFAULT 'NOT_STARTED',
    "raw_channel_status"     varchar(128) DEFAULT NULL,
    "submitted_by"           int8         DEFAULT NULL,
    "submitted_time"         timestamp    DEFAULT NULL,
    "completed_time"         timestamp    DEFAULT NULL,
    "row_version"            int8         NOT NULL DEFAULT 0,
    "create_user"            int8         DEFAULT NULL,
    "create_time"            timestamp    NOT NULL,
    "update_user"            int8         DEFAULT NULL,
    "update_time"            timestamp    DEFAULT NULL,
    "deleted"                int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_onboarding_application" IS '商户渠道进件申请表';

-- changeset continew:merchant-phase1-kyc-version-postgresql
-- comment 版本化KYC数据表
CREATE TABLE IF NOT EXISTS "biz_kyc_version" (
    "id"                             int8         NOT NULL,
    "tenant_id"                      int8         NOT NULL,
    "merchant_id"                    int8         NOT NULL,
    "onboarding_application_id"      int8         DEFAULT NULL,
    "version_no"                     int4         NOT NULL,
    "previous_version_id"            int8         DEFAULT NULL,
    "source_kyc_version_id"          int8         DEFAULT NULL,
    "requirement_version"            varchar(64)  NOT NULL,
    "status"                         varchar(32)  NOT NULL DEFAULT 'DRAFT',
    "saved_step"                     int2         NOT NULL DEFAULT 1,
    "legal_name"                     varchar(200) NOT NULL,
    "legal_identifier_ciphertext"    bytea        DEFAULT NULL,
    "legal_identifier_hash"          char(64)     DEFAULT NULL,
    "legal_identifier_masked"        varchar(64)  DEFAULT NULL,
    "legal_identifier_key_version"   varchar(32)  DEFAULT NULL,
    "license_issue_date"             date         DEFAULT NULL,
    "license_expiry_date"            date         DEFAULT NULL,
    "business_scope"                 text         DEFAULT NULL,
    "address_payload_ciphertext"     bytea        DEFAULT NULL,
    "person_payload_ciphertext"      bytea        DEFAULT NULL,
    "shareholder_payload_ciphertext" bytea        DEFAULT NULL,
    "payload_key_version"            varchar(32)  DEFAULT NULL,
    "settlement_account_ciphertext"  bytea        DEFAULT NULL,
    "settlement_account_hash"        char(64)     DEFAULT NULL,
    "settlement_account_masked"      varchar(64)  DEFAULT NULL,
    "settlement_key_version"         varchar(32)  DEFAULT NULL,
    "pricing_version_id"             int8         DEFAULT NULL,
    "frozen_time"                    timestamp    DEFAULT NULL,
    "row_version"                    int8         NOT NULL DEFAULT 0,
    "create_user"                    int8         DEFAULT NULL,
    "create_time"                    timestamp    NOT NULL,
    "update_user"                    int8         DEFAULT NULL,
    "update_time"                    timestamp    DEFAULT NULL,
    "deleted"                        int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_kyc_version" IS '版本化KYC数据表';

-- changeset continew:merchant-phase1-kyc-attachment-postgresql
-- comment KYC私有附件元数据表
CREATE TABLE IF NOT EXISTS "biz_kyc_attachment" (
    "id"                int8         NOT NULL,
    "tenant_id"         int8         NOT NULL,
    "kyc_version_id"    int8         NOT NULL,
    "evidence_type"     varchar(64)  NOT NULL,
    "storage_object_id" varchar(255) NOT NULL,
    "original_name"     varchar(255) NOT NULL,
    "extension"         varchar(32)  DEFAULT NULL,
    "declared_mime"     varchar(128) DEFAULT NULL,
    "detected_mime"     varchar(128) DEFAULT NULL,
    "size_bytes"        int8         NOT NULL,
    "sha256"            char(64)     NOT NULL,
    "scan_status"       varchar(32)  NOT NULL DEFAULT 'PENDING',
    "validation_status" varchar(32)  NOT NULL DEFAULT 'PENDING',
    "sort"              int4         NOT NULL DEFAULT 999,
    "create_user"       int8         DEFAULT NULL,
    "create_time"       timestamp    NOT NULL,
    "update_user"       int8         DEFAULT NULL,
    "update_time"       timestamp    DEFAULT NULL,
    "deleted"           int8         NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);
COMMENT ON TABLE "biz_kyc_attachment" IS 'KYC私有附件元数据表';
