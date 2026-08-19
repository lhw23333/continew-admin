# Source Requirements and Visual Evidence

## Authoritative source

- [支付代理商管理系统需求规格说明书.docx](../../../../deliverables/支付代理商管理系统需求规格说明书.docx)
- Source version: V1.0, 2026-08-19
- Evidence method: read-only exploration of the reference management system plus local ContiNew 4.1.0 code/runtime inspection.
- Privacy rule: screenshots retain only safe metric, filter, or blank-form areas and do not intentionally expose complete identity, bank-card, mobile, transaction-serial, QR, or attachment data.

## Phase-one evidence catalog

| Evidence | Capability | Purpose |
|---|---|---|
| [Workbench metrics](../assets/01-dashboard.png) | `operations-workbench` | Metric layout and daily comparison evidence |
| [Blank add-agent form](../assets/02-agent-add-form.png) | `agent-management` | Generated account, agent name, phone, remark fields |
| [Agent filters](../assets/03-agent-management.png) | `agent-management` | Agent ID/name filters and subordinate create entry |
| [Blank merchant form](../assets/04-merchant-add-form.png) | `merchant-master` | Generated operator/reviewer identities and merchant fields |
| [Onboarding wizard](../assets/05-onboarding-wizard.png) | `merchant-onboarding` | Five-step wizard and required evidence types |
| [Review filters](../assets/06-onboarding-review.png) | `flowable-approval` | Merchant/channel/human/AI/sub-agent review filters |
| [Account authorization](../assets/13-account-security.png) | `sensitive-data-protection` | Payment password, SMS verification, and controlled attachment upload |

## Future-phase evidence retained for traceability

These screenshots are included to preserve the initial requirements baseline but are not implementation scope for this change.

| Evidence | Future capability | Deferred scope |
|---|---|---|
| [Split whitelist filters](../assets/08-split-whitelist.png) | split-settlement-whitelist | Payee whitelist and sensitive query/export controls |
| [Withdrawal filters](../assets/09-withdrawal-monitoring.png) | withdrawal-operations | Withdrawal, reviewer state, fee, and channel result |
| [Order filters](../assets/10-order-monitoring.png) | payment-order-management | Merchant/platform/channel order traceability |
| [Merchant daily report](../assets/11-merchant-daily-report.png) | transaction-reporting | Daily totals, fees, refunds, statistics, and export |
| [Voucher management](../assets/12-voucher-management.png) | voucher-management | Voucher lifecycle and bulk issuance |

## Traceability convention

- `FR-*` identifiers trace to functional requirements in the source DOCX.
- `NFR-*` identifiers trace to non-functional requirements in the source DOCX.
- `AC-*` identifiers trace to source acceptance criteria.
- `WF-*`, `CHN-*`, `SEC-*`, and `OPS-*` identify requirements derived from the ContiNew/Flowable technical assessment and are linked to the closest source business requirement where applicable.
