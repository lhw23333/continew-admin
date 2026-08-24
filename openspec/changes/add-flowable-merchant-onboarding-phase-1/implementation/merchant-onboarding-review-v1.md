# Merchant Onboarding Review V1

## Controlled resource

The reviewed BPMN resource is packaged as
`workflow-definitions/merchant-onboarding-review-v1.bpmn20.xml`, outside Flowable's automatic `processes/` scan.
`flowable.check-process-definitions` is disabled, so application startup cannot silently create an unverified process
version. Operations deploy the resource through `WorkflowDeploymentService` using the stable contract exposed by
`MerchantOnboardingReviewWorkflowDefinition`.

Stable identifiers:

| Node | Type | Purpose |
|---|---|---|
| `start` | Start event | Submitted onboarding enters review routing |
| `aiRoutingGateway` | Exclusive gateway | Selects the optional automated-review hook |
| `aiReviewTask` | Service task | Revalidates identifier-only context without making a human decision |
| `reviewTask` | User task | Merchant or risk reviewer human decision |
| `reviewEscalationTimer` | Boundary timer | Interrupts an overdue ordinary review after 48 hours |
| `escalatedReviewTask` | User task | Risk-reviewer-only overdue handling |
| `reviewDecisionGateway` | Exclusive gateway | Routes the validated human action |
| `supplementTask` | User task | Applicant-owned supplementation and resubmission |
| `approvedEnd` | End event | Human review approved |
| `rejectedEnd` | End event | Human review rejected |

## Optional AI boundary

The optional branch is selected only when `riskLevel` is not `UNASSESSED`. The v1 hook runs
`MerchantOnboardingAiReviewGuardDelegate`, which revalidates the complete Flowable variable map through the project
allowlist. It writes no KYC data, model payload, or decision variable and cannot skip `reviewTask`. An AI provider may
later add independently persisted model/rule evidence behind a reviewed adapter, but a new BPMN resource and
compatibility test are required before that integration is enabled.

This deliberately preserves the specification rule that AI output and human decisions are independent. Whether the AI
branch executes or is skipped, the process always waits for an authorized human review.

## Human paths

- `reviewTask` candidates are `MERCHANT_REVIEWER` and `RISK_REVIEWER`; server-side tenant, agent, merchant, enabled-user,
  and applicant-separation checks remain authoritative.
- `APPROVE` reaches `approvedEnd`.
- `REJECT` reaches `rejectedEnd` and requires the domain-validated sanitized opinion.
- `REQUEST_SUPPLEMENT` creates `supplementTask`, assigned to `applicantId`.
- `RESUBMIT` returns to the same stable `reviewTask`; the domain service freezes a new linked KYC version before task
  completion.
- An ordinary review outstanding for 48 hours is interrupted and replaced by `escalatedReviewTask`, whose only candidate
  group is `RISK_REVIEWER`.

Flowable owns these task and routing states only. Application/KYC status changes and immutable review evidence continue
to be written by `OnboardingReviewService` in the same validated action transaction.

## Verification

Shared MySQL 8.4 and PostgreSQL integration tests deploy the exact classpath bytes through the immutable deployment
service and verify:

- the optional AI branch executes only with identifier-only variables and still stops at human review;
- `UNASSESSED` skips the AI hook;
- the 48-hour timer creates an executable job and routes to the risk-only escalation task;
- approve and reject reach distinct terminal nodes;
- supplement creates an applicant task, resubmit returns to human review, and the supplemented KYC version remains
  domain-owned;
- candidate roles, transfer, separation of duties, concurrent completion, and immutable review records still apply; and
- transactional outbox delivery starts the same formal process resource idempotently.

All fixtures use synthetic tenant, merchant, application, user, and KYC identifiers.
