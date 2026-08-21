# Privileged Reveal Dependency

Task 4.3 cannot be completed safely before the mandatory business-scope services exist. The execution-order change was approved on August 20, 2026.

Available foundations:

- ContiNew permission checks (`@SaCheckPermission`) can enforce an independent reveal permission.
- Existing RSA password transport and `PasswordEncoder.matches` behavior can support password-based step-up authentication through a project-owned port.
- Encrypted value objects and versioned-key reveal are available from task 4.2.

Originally missing authoritative dependencies:

- task 5.1 agent descendant-scope authorization service;
- task 6.1 merchant ownership/repository and merchant-scope authorization;
- an immutable security-audit repository/table contract used by reveal actions.

The reveal API must not use tenant equality, client-provided agent IDs, or generic role membership as a substitute for merchant scope. Implementing a permissive placeholder would violate the `sensitive-data-protection` specification.

Approved execution order:

1. Complete task 5.1 and expose the authoritative tenant/agent descendant-scope service.
2. Complete task 6.1 and expose merchant ownership/repository scope checks.
3. Resume task 4.3 with both scope services and the immutable audit table available.
4. Continue tasks 4.4–4.7, then resume Agent Management at 5.2 and Merchant Master at 6.2.

The task file physically places 5.1 and 6.1 before 4.3 so `openspec instructions apply` returns this dependency-safe order without duplicating or renumbering tasks.

## Resolution

The dependency was fully resolved on August 20, 2026:

- task 5.1 provides tenant-bound agent closure authorization;
- task 6.1 provides merchant ownership, direct operator/reviewer identity mapping, and merchant-scope authorization;
- task 4.3 adds the privileged reveal service and API, ContiNew permission and RSA-password step-up adapters, no-store response handling, server-owned masking/field policy, and an append-only `biz_security_audit` repository/table;
- MySQL 8.4 and PostgreSQL 16 integration tests prove merchant scope behavior and reject direct audit `UPDATE`/`DELETE` statements.

Task 4.3 is therefore complete. The MySQL trigger migration prerequisite is documented in `docs/release/security-audit-immutability.md`.
