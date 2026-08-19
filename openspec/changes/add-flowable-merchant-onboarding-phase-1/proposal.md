## Why

The validated requirements baseline describes a payment-agent platform whose merchant onboarding, KYC review, supplementation, limit adjustment, and sensitive-data controls are not present in ContiNew Admin 4.1.0. Phase one must standardize those requirements and add a durable human-approval engine without duplicating KYC data inside the workflow database.

## What Changes

- Add agent hierarchy and agent-scoped merchant ownership on top of ContiNew tenant and RBAC capabilities.
- Add merchant master records with separate operator and reviewer identities.
- Add versioned five-step merchant onboarding for channel selection, evidence, legal/KYC data, settlement account, pricing, and operating-platform proof.
- Integrate Flowable 7.x as the process engine for merchant review, supplementation, rejection, approval, and limit-adjustment approvals.
- Add a Flowable task center integrated with ContiNew users, roles, tenants, agent-tree data permissions, messages, and audit logs.
- Add channel-onboarding adapters and a domain-owned channel state machine; Flowable SHALL NOT own payment or channel truth.
- Add encryption, masking, privileged reveal, private attachment access, and immutable security audit requirements for sensitive KYC data.
- Add a workbench view for onboarding counts, overdue tasks, failures, and notifications.
- Preserve downstream settlement, withdrawals, transactions, reports, and vouchers as future phases documented in the evidence catalog.
- **BREAKING**: production logging and export behavior must exclude or redact sensitive request/response fields before KYC endpoints are enabled.

### Phase-One Non-goals

- Ledger entries, balances, freezing/unfreezing, split settlement, withdrawals, refunds, reconciliation, chargebacks, and voucher issuance.
- LiteFlow, Flowable IDM, Flowable Form, Flowable CMMN/DMN, Flowable REST applications, and commercial Flowable UI products.
- Storing raw identity numbers, bank account numbers, mobile numbers, passwords, or permanent attachment URLs in Flowable variables.
- Replacing ContiNew authentication, authorization, tenant management, file storage, messaging, or audit foundations.

## Capabilities

### New Capabilities

- `agent-management`: Agent hierarchy, scoped administration, account lifecycle, pricing boundaries, and merchant defaults.
- `merchant-master`: Merchant identity, ownership, operator/reviewer accounts, lifecycle, uniqueness, and channel summary.
- `merchant-onboarding`: Versioned five-step KYC onboarding, draft recovery, evidence, settlement account, pricing, and final submission.
- `flowable-approval`: Flowable process integration, task center, candidate resolution, supplementation, audit history, and process versioning.
- `channel-onboarding`: Channel adapters, request signing, callback verification, idempotent state mapping, polling, and recovery.
- `merchant-limit-adjustment`: Channel and platform limit-adjustment request, approval, history, and effective-state tracking.
- `sensitive-data-protection`: Encryption, masking, privileged reveal, private attachment access, export controls, and audit sanitation.
- `operations-workbench`: Onboarding operational metrics, overdue work, failures, notifications, and drill-down navigation.

### Modified Capabilities

None. The repository has no existing OpenSpec capability specifications.

## Impact

- New backend modules are expected for merchant/onboarding domain logic and Flowable integration; Flowable APIs must be hidden behind a project-owned workflow port.
- New Vue views are required for merchant forms, onboarding wizard, approval task center, review comparison, and operational workbench.
- New domain tables, Flowable runtime/history tables in a dedicated schema or prefix, Liquibase migrations, indexes, retention policies, and backup procedures are required.
- ContiNew user IDs, role codes, tenant IDs, messages, file object IDs, and audit facilities are reused.
- New integration boundaries are required for channel clients, OCR/risk services, object storage, SMS, and scheduled recovery.
- CI must stop skipping tests and add Testcontainers/WireMock coverage before channel or sensitive-data functionality is released.
- Licensing review must record ContiNew Starter LGPL obligations and Flowable Apache-2.0 usage.
