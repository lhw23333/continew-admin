# add-flowable-merchant-onboarding-phase-1

Standardize the phase-one merchant onboarding platform requirements on ContiNew Admin with Flowable-backed approvals, using the validated requirements document and safe screenshots.

## Status

- Schema: `spec-driven`
- Artifacts: 4/4 complete
- Capability specs: 8
- Requirements: 86
- Acceptance scenarios: 104
- Implementation tasks: 97
- Safe screenshots: 12
- OpenSpec strict validation: passing

## Planning Artifacts

- [Proposal](proposal.md)
- [Technical design](design.md)
- [Implementation tasks](tasks.md)
- [Source requirements and screenshot evidence](references/source-requirements.md)

## Capability Specifications

- [Agent management](specs/agent-management/spec.md)
- [Merchant master](specs/merchant-master/spec.md)
- [Versioned merchant onboarding](specs/merchant-onboarding/spec.md)
- [Flowable approval integration](specs/flowable-approval/spec.md)
- [Channel onboarding](specs/channel-onboarding/spec.md)
- [Merchant limit adjustment](specs/merchant-limit-adjustment/spec.md)
- [Sensitive-data protection](specs/sensitive-data-protection/spec.md)
- [Operations workbench](specs/operations-workbench/spec.md)

## Scope Boundary

Phase one introduces Flowable 7.x for human approval and task history. It does not introduce LiteFlow and does not implement ledger, split settlement, withdrawal execution, refunds, reconciliation, chargebacks, or voucher issuance. Those downstream requirements remain linked in the evidence catalog for future OpenSpec changes.

Raw KYC and financial data remain in encrypted, versioned domain tables. Flowable variables contain approved identifiers and non-sensitive routing metadata only.

## CLI Commands

```powershell
openspec status --change add-flowable-merchant-onboarding-phase-1
openspec show add-flowable-merchant-onboarding-phase-1
openspec validate add-flowable-merchant-onboarding-phase-1 --type change --strict --no-interactive
openspec instructions apply --change add-flowable-merchant-onboarding-phase-1 --json
```
