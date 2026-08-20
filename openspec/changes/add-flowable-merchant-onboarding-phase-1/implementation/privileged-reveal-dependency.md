# Privileged Reveal Dependency

Task 4.3 cannot be completed safely before the mandatory business-scope services exist.

Available foundations:

- ContiNew permission checks (`@SaCheckPermission`) can enforce an independent reveal permission.
- Existing RSA password transport and `PasswordEncoder.matches` behavior can support password-based step-up authentication through a project-owned port.
- Encrypted value objects and versioned-key reveal are available from task 4.2.

Missing authoritative dependencies:

- task 5.1 agent descendant-scope authorization service;
- task 6.1 merchant ownership/repository and merchant-scope authorization;
- an immutable security-audit repository/table contract used by reveal actions.

The reveal API must not use tenant equality, client-provided agent IDs, or generic role membership as a substitute for merchant scope. Implementing a permissive placeholder would violate the `sensitive-data-protection` specification. Task 4.3 therefore remains open and must resume immediately after tasks 5.1 and 6.1 provide the authoritative scope checks (with the audit table added before API enablement).
