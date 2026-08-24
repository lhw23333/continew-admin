# BPMN Deployment and In-flight Version Policy

## Scope

Task 8.8 establishes a project-owned deployment boundary for Flowable process definitions. It does not introduce the
production `merchant-onboarding-review-v1` model; that model remains task 8.9. Deployment callers use
`WorkflowDeploymentService` and never receive Flowable deployment or process-definition entities.

## Deployment contract

Each trusted deployment command contains:

- ContiNew tenant ID and deploying user ID;
- a deployment name and one BPMN resource ending in `.bpmn20.xml` or `.bpmn`;
- the expected stable process-definition key;
- a positive project contract version;
- the required stable BPMN node IDs and their expected node types.

The adapter accepts one executable process definition per resource. It validates the deployed definition key, Flowable
tenant ID, and required node contract before recording the deployment. Missing nodes, changed node types, duplicate
contract node IDs, malformed BPMN, multiple definitions, and process-key mismatches fail closed with sanitized workflow
error codes. The BPMN deployment and metadata insert share the Spring transaction, so failed validation leaves neither
a usable Flowable definition nor project metadata.

## Immutable resource identity

The complete BPMN bytes are hashed with SHA-256 before deployment. Table `biz_workflow_deployment` records only:

- tenant and actor identifiers;
- Flowable deployment and process-definition identifiers;
- stable process key and engine version;
- project contract version;
- resource name and SHA-256 digest;
- deployment time truncated to millisecond precision.

The BPMN body is retained by Flowable's deployment resource tables and is not duplicated in the business metadata
table. Unique constraints prevent a tenant/process key from binding one contract version to different bytes or binding
the same bytes to multiple contract versions. Repeating the exact resource and contract returns the existing immutable
metadata without creating another Flowable version.

MySQL and PostgreSQL triggers reject every `UPDATE` and `DELETE` against `biz_workflow_deployment`. Corrections therefore
require a new contract/resource version; operators must not rewrite deployment evidence.

## Stable key and node rules

- A process key is a long-lived integration identifier and may contain letters, digits, `.`, `_`, and `-`; renaming it
  creates a different process family.
- Node IDs referenced by task authorization, domain actions, timers, notifications, or history consumers are part of
  the project contract and must keep the same semantic node type in compatible versions.
- New internal nodes may be added when compatibility tests pass, but required stable nodes cannot disappear or change
  type under the same process family.
- Resource files and contract versions are append-only release artifacts. Production deployment must use reviewed bytes,
  not an editor-generated mutable resource at application startup.

## In-flight instance policy

Deploying a newer definition never migrates active instances automatically:

1. New starts by process key and tenant use the latest deployed Flowable definition.
2. Existing instances retain their original `processDefinitionId` and continue on that definition.
3. Suspension or migration requires a separately approved compatibility plan and the task 13.2 operational runbook.
4. Application rollback blocks new starts through feature flags but retains additive Flowable and deployment metadata;
   it does not delete history or mutate active instances.

## Automated verification

The shared Testcontainers test executed by both `MySqlApplicationIT` and `PostgreSqlApplicationIT` proves that:

- v1 can remain active while v2 is deployed;
- new instances use v2 while the active v1 instance keeps its original definition ID;
- an exact deployment retry is idempotent;
- the same contract version cannot be rebound to different BPMN bytes;
- a missing stable node or changed stable node type rolls back the attempted deployment;
- metadata counts remain aligned with successful Flowable definitions; and
- database triggers reject metadata updates and deletes.

Only synthetic process keys and synthetic process instances are used by these tests.
