# Subordinate Agent Identity Dependency

Task 5.3 was paused because ContiNew user creation required organization and credential decisions that were not represented in the current agent model or OpenSpec artifacts.

## Verified constraints

- `UserReq` requires a `deptId`, at least one `roleId`, username, nickname, gender, and an RSA-encrypted creation password.
- `biz_agent` currently binds one ContiNew `user_id` but does not bind a ContiNew department ID.
- No phase-one agent-administrator role code or menu bundle is defined in seed data.
- The requirement explicitly forbids returning a password, but no one-time activation/reset delivery policy is selected.
- Creating the user and role first without a matching `biz_agent` transaction can leave an orphan identity; creating the agent first can leave an unusable agent when identity creation fails.

## Required decisions

1. **Department model**
   - one ContiNew department per agent, mirroring the agent parent hierarchy (recommended);
   - all agents share one tenant department and authorization remains exclusively in `biz_agent_closure`;
   - another explicit mapping.
2. **Role code**
   - define the subordinate agent administrator role code and its initial menu/button permissions (recommended code: `AGENT_ADMIN`).
3. **Credential bootstrap**
   - the creating administrator supplies an RSA-encrypted temporary password;
   - the account is enabled with `must_change_password=true` and can use only the password-change flow until replacement.

## Recommended implementation after confirmation

- add immutable `dept_id` to `biz_agent` and create the department in the same server transaction as the ContiNew user, role binding, agent row, and closure rows;
- resolve the parent department from the server-side parent agent, never from the request;
- generate a collision-safe username from the agent number plus a server suffix;
- bind the configured `AGENT_ADMIN` role;
- store the encoded administrator-supplied temporary password and return only the generated username plus credential status;
- roll back user, role, department, agent, and closure writes together on any failure.

## Resolution

The recommended model was confirmed on August 21, 2026 and implemented:

- `biz_agent.dept_id` binds each provisioned agent to one ContiNew department;
- the department hierarchy mirrors the server-resolved agent hierarchy, while business authorization remains authoritative in `biz_agent_closure`;
- the system role `AGENT_ADMIN` is seeded with self data scope;
- usernames are generated from normalized agent number plus a collision-resistant server ID suffix;
- the temporary password is RSA-protected in transport, BCrypt-encoded by ContiNew, never returned, and excluded from generic logging;
- the generated user is enabled with `must_change_password=true` and returned as `PASSWORD_CHANGE_REQUIRED`;
- the user table does not duplicate the contact mobile; the number remains AES-GCM protected in `biz_agent`;
- department, user, role binding, agent, and closure records join one transaction and rollback together.

MySQL 8.4 and PostgreSQL 16 integration tests verify the complete transaction and prove a duplicate agent-number failure does not create orphan users or departments.
