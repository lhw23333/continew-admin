## 1. Dependency, Licensing, and Build Baseline

- [x] 1.1 Create a compatibility spike that boots ContiNew 4.1.0 with a candidate Flowable 7.x process starter on Java 17 and Spring Boot 3.3.12
- [x] 1.2 Run Maven dependency convergence and document/pin compatible Spring, MyBatis, Jackson, Liquibase, and logging versions
- [x] 1.3 Record Apache-2.0 Flowable usage, ContiNew Starter LGPL obligations, notices, and generated SBOM in the release documentation
- [x] 1.4 Remove global test skipping and add separate fast-unit and integration-test Maven profiles
- [x] 1.5 Add CI gates for backend tests, frontend typecheck/lint/tests, OpenSpec strict validation, dependency vulnerability scan, and secret scan

## 2. Module and Application Boundaries

- [x] 2.1 Add the `continew-extension-workflow` Maven module with no merchant-domain implementation dependencies
- [x] 2.2 Add the `continew-merchant` Maven module for agent, merchant, KYC, review, and limit domain/application services
- [x] 2.3 Add the `continew-channel` Maven module with project-owned channel ports and no Flowable API dependency
- [x] 2.4 Define module-owned API/DTO packages and architecture tests preventing domain services from importing Flowable implementation classes
- [x] 2.5 Register new modules in `continew-server` and verify application startup with both MySQL and PostgreSQL test profiles

## 3. Database Schema and Migration

- [x] 3.1 Select a Flowable schema or table prefix and add reviewed production migrations with automatic schema update disabled
- [x] 3.2 Add Liquibase migrations for agent, agent closure/path, merchant, onboarding application, KYC version, and KYC attachment tables
- [x] 3.3 Add migrations for pricing versions, review records, workflow mappings, limit requests, outbox events, and channel events
- [x] 3.4 Add unique constraints for merchant legal identity hash, active onboarding idempotency key, workflow business key, outbox key, and channel event key
- [x] 3.5 Add tenant/agent/status/time composite indexes for list, task, callback, and workbench queries and verify query plans on representative data
- [x] 3.6 Add migration rollback/forward validation tests and database backup/restore instructions for domain and Flowable tables

## 4. Sensitive Data and Logging Foundation

- [x] 4.1 Replace repository/default production encryption keys and channel secrets with environment or approved secret-manager references and fail startup on placeholders
- [x] 4.2 Implement encrypted identity, bank-account, and mobile value objects with key version, normalized keyed hash, and masked display value

### Authorization Prerequisites for Privileged Reveal

> Execution-order override: complete authoritative agent and merchant scope before exposing privileged reveal.

- [x] 5.1 Implement agent hierarchy entities, closure/path maintenance, tenant ownership, lifecycle, repository, and descendant-scope authorization service
- [x] 6.1 Implement merchant master, ownership, lifecycle, operator/reviewer identity mappings, repository, and agent-scope authorization

- [x] 4.3 Implement server-side masking policies and privileged-reveal API with business-scope check, reason, step-up authentication, no-store headers, and immutable audit
- [x] 4.4 Configure KYC, password, channel, file, reveal, and export endpoints to exclude or sanitize request/response bodies before access-log persistence
- [x] 4.5 Implement the Flowable variable name/type allowlist and automated rejection tests for raw KYC objects, credentials, binary data, and attachment URLs
- [x] 4.6 Configure private KYC object storage, short-lived access, MIME/content checks, size/count limits, hashing, malware-scan port, and attachment-access audit
- [x] 4.7 Add automated scans proving complete synthetic identity, bank, mobile, and password values do not appear in Flowable variables, logs, errors, caches, or exports

## 5. Agent Management

> Task 5.1 is executed before task 4.3 as a security prerequisite; continue this capability with task 5.2.

- [x] 5.2 Implement scoped agent list/detail APIs with combined filters, stable pagination, and sibling-agent enumeration tests
- [x] 5.3 Implement subordinate creation with generated login identity, server-resolved parent, validation, and atomic ContiNew user/role binding
- [x] 5.4 Implement allowed profile edits, self-disable prevention, subordinate enable/disable, session revocation, and password-reset audit
- [x] 5.5 Implement promotion code binding and active/disabled ownership validation without client-selectable agent reassignment
- [x] 5.6 Implement immutable agent pricing versions, parent-bound validation, effective time, uniqueness, and change audit
- [x] 5.7 Implement versioned merchant defaults and draft-time inheritance without rewriting existing merchant/onboarding history
- [x] 5.8 Implement Vue agent filters, list actions, blank create/edit forms, pricing/default editors, permission visibility, and conflict/error states

## 6. Merchant Master

> Task 6.1 is executed before task 4.3 as a security prerequisite; continue this capability with task 6.2.
>
> Execution-order override: task 6.3 requires authoritative channel relationship/status summaries. Execute
> `6.4 → 6.5 → 6.6 → 6.3`, then return to the scoped summary API after the channel-state boundary is available.

- [x] 6.2 Implement normalized legal-subject uniqueness with concurrent-create tests and deterministic duplicate response
- [x] 6.3 Implement scoped merchant search/detail APIs with channel/pricing summary and state/permission-derived actions
- [x] 6.4 Implement atomic merchant creation with distinct generated operator/reviewer identities and required field validation
- [x] 6.5 Implement ordinary profile edit rules and controlled re-verification routing for certified identity, ownership, and settlement-account changes
- [x] 6.6 Implement merchant enable/disable with reason, downstream-operation policy, session impact, and immutable audit
- [x] 6.7 Implement Vue merchant filters, list, create/edit forms, channel summary, lifecycle action confirmation, and responsive validation

## 7. Versioned Five-Step Onboarding

- [x] 7.1 Implement channel eligibility and requirement-version query using tenant, agent product, merchant type, and channel status
- [x] 7.2 Implement onboarding draft, optimistic version, step-completion state, explicit save, reload recovery, and save-conflict response
- [x] 7.3 Implement same-merchant KYC reuse with provenance, field allowlist, channel-specific exclusions, and expiry revalidation
- [x] 7.4 Implement evidence collection with private file object references, required-type validation, optional attachment limits, and scan status
- [x] 7.5 Implement versioned legal subject, legal representative, operator, beneficiary, and individual/corporate shareholder commands and validation
- [x] 7.6 Implement ordinary/accelerated settlement-account modes with encrypted account data and pluggable verification port
- [x] 7.7 Implement pricing-version selection and parent-bound revalidation at save and final submission
- [x] 7.8 Implement multiple operating-platform records and independently versioned proof attachments
- [x] 7.9 Implement final preview using the exact saved business/KYC/pricing versions and sanitized account/identity summaries
- [x] 7.10 Implement idempotent final submission, frozen submitted KYC version, workflow outbox event, and duplicate-click tests

### Workflow Prerequisites for Supplementation

> Execution-order override: task 7.11 must bind a new KYC version to an authoritative Flowable process/task and
> validated supplementation action. Execute `8.1 → 8.2 → 8.3 → 8.4 → 8.5` before task 7.11, then return to
> task 7.12 after the supplementation domain path is complete.

- [ ] 7.11 Implement supplementation as a new linked KYC version with field/attachment diff and no direct mutation of submitted versions
- [ ] 7.12 Implement Vue five-step wizard, local unsaved state, explicit save indicator, reload recovery, preview, conflict resolution, and safe attachment viewer

## 8. Flowable Adapter and Task Center

- [x] 8.1 Configure Flowable process engine, dedicated schema/prefix, history level, async executor, job monitoring, and production schema-update policy
- [ ] 8.2 Implement project-owned workflow commands/DTOs and adapter for start, claim, unclaim, complete, query todo/done, and history
- [ ] 8.3 Implement business-key/workflow mapping with tenant, business type/ID/version, process definition/version, process instance, and unique idempotency
- [ ] 8.4 Implement ContiNew tenant, user ID, role-code candidate group, enabled-user, and agent/merchant-scope resolution for every task query/action
- [ ] 8.5 Implement approve, reject, request-supplement, resubmit, transfer, and opinion validation with immutable review records
- [ ] 8.6 Implement optimistic concurrent task completion and domain-version conflict handling tests
- [ ] 8.7 Implement transactional outbox producer/consumer for domain decisions and Flowable commands/events with retries and repair status
- [ ] 8.8 Implement BPMN deployment verification, stable process keys/node IDs, immutable resource metadata, and in-flight version policy
- [ ] 8.9 Implement `merchant-onboarding-review-v1` BPMN with optional AI step, human review, approve/reject/supplement/resubmit paths, and timers
- [ ] 8.10 Implement Vue todo, claimed, done, process-history, task detail, sanitized business summary, supplementation diff, and action dialogs
- [ ] 8.11 Implement assignment/result/overdue ContiNew messages with notification idempotency and authorized deep links

## 9. Channel Onboarding Integration

- [ ] 9.1 Define channel adapter interfaces and normalized command/result/event models for submit, status query, signing link, account info, and limit adjustment
- [ ] 9.2 Implement a synthetic/reference channel adapter used by automated tests before integrating any production channel
- [ ] 9.3 Implement versioned endpoint/product/timeout/status-map/key-reference configuration with secret-safe loading
- [ ] 9.4 Implement outbound signing/encryption, timestamp/nonce, business serial, trace correlation, and sanitized transport audit
- [ ] 9.5 Implement callback endpoint signature/certificate/timestamp/replay validation before event persistence
- [ ] 9.6 Implement channel event idempotency, raw-code retention, versioned normalized mapping, and out-of-order non-regression rules
- [ ] 9.7 Implement timeout/circuit-breaker/bulkhead/retry policy distinguishing safe queries from uncertain non-idempotent commands
- [ ] 9.8 Implement SnailJob polling/recovery for supported uncertain states with retry count, next retry, alert, and authorized manual repair
- [ ] 9.9 Implement channel evidence streaming or short-lived channel-scoped file access with object/hash audit
- [ ] 9.10 Implement process QR/signing-link generation with tenant/application/action/expiry binding and tamper/expiry tests

## 10. Limit Adjustment

- [ ] 10.1 Implement limit-adjustment entity, original/requested/normalized/effective values, eligibility, uniqueness, history, and audit
- [ ] 10.2 Implement configurable minimum/maximum/currency precision/thousand-rounding validation and confirmation preview
- [ ] 10.3 Implement `merchant-limit-adjustment-v1` BPMN and workflow mapping for submit, approve, reject, channel submit/query, effective, and failed outcomes
- [ ] 10.4 Implement conflict detection when effective limit or configuration changes during approval
- [ ] 10.5 Implement scoped list/detail/history APIs and Vue create/history/review views
- [ ] 10.6 Add tests proving human approval alone does not make a limit effective before required channel confirmation

## 11. Operations Workbench and Observability

- [ ] 11.1 Implement scoped workbench metrics for drafts, submissions, review/supplement tasks, channel processing, outcomes, and overdue work with as-of time
- [ ] 11.2 Implement unavailable/stale/zero metric distinction, controlled refresh, and permission-preserving drill-down filters
- [ ] 11.3 Implement operational failure queue for uncertain channel calls, exhausted retries, workflow/domain drift, scan failures, and overdue tasks
- [ ] 11.4 Correlate HTTP trace, domain command, outbox, process, task, channel serial, and scheduled-job IDs without sensitive payloads
- [ ] 11.5 Add metrics and alerts for Flowable jobs, task age, outbox lag, callback failures, channel latency, retry exhaustion, and state invariant violations
- [ ] 11.6 Implement Vue operational workbench, notification views, failure details, and authorized repair controls

## 12. Automated Acceptance and Security Testing

- [ ] 12.1 Add Testcontainers integration environment for supported database and Redis versions plus private-object-storage test double/container
- [ ] 12.2 Add Flowable integration tests deploying BPMN and covering every terminal path, supplementation loop, timers, candidate groups, tenant isolation, and concurrent completion
- [ ] 12.3 Add WireMock channel tests for valid/invalid signatures, duplicate/late callbacks, timeouts, malformed payloads, status mapping, and uncertain results
- [ ] 12.4 Add authorization tests for tenant isolation, agent siblings, merchant enumeration, task visibility, attachment access, reveal access, and direct API calls
- [ ] 12.5 Add encryption/masking/logging tests proving sensitive values are ciphertext at rest and absent from Flowable history, logs, cache, errors, and ordinary responses
- [ ] 12.6 Add frontend component and E2E tests for agent/merchant forms, wizard recovery, preview, permissions, masking/reveal, review actions, conflicts, and workbench states
- [ ] 12.7 Create synthetic acceptance fixtures mapped to every Requirement/Scenario in the eight phase-one OpenSpec capability files
- [ ] 12.8 Run load/concurrency tests for list queries, draft saves, duplicate submission, task completion, callbacks, outbox processing, and workbench aggregation

## 13. Release, Migration, and Runbooks

- [ ] 13.1 Document and test Flowable/domain migration order, maintenance windows, backups, forward recovery, and non-destructive application rollback
- [ ] 13.2 Create BPMN deployment and in-flight instance compatibility/migration runbook
- [ ] 13.3 Create KYC encryption-key rotation, reveal audit, attachment quarantine, retention, legal hold, and deletion runbook
- [ ] 13.4 Create outbox/channel/workflow inconsistency detection and idempotent repair runbook that forbids direct history/state edits
- [ ] 13.5 Add per-tenant and per-channel feature flags and execute staged rollout with synthetic/test tenants before production enablement
- [ ] 13.6 Complete security review, license/SBOM review, disaster-recovery exercise, and all OpenSpec strict acceptance gates before enabling real KYC data
