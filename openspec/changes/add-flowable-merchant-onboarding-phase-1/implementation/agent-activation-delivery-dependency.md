# Agent Activation Delivery Dependency

Task 5.4 was paused while selecting a lower-cost credential bootstrap method.

## Superseded activation options

SMS codes, email links, and token-based activation were rejected to reduce development and message-delivery costs.

## Resolution

The confirmed approach on August 21, 2026 is administrator-supplied temporary password plus mandatory first-login password change:

- the creation/reset request carries the temporary password through the existing RSA transport;
- `sys_user.must_change_password` is independent from the normal password-expiration-days option;
- the user is enabled but all business routes are blocked until the password is changed;
- `/user/profile/password`, user info, routes, and logout remain available during the forced-change state;
- successful password change clears `must_change_password`, updates `pwd_reset_time`, and logs the user out;
- administrator resets restore `must_change_password=true` and revoke active sessions;
- temporary passwords are never returned or persisted in logs/audit.

Task 5.4 also implements scoped profile edits, self-disable prevention, subordinate enable/disable, user-status synchronization, session revocation, and immutable profile/lifecycle/password-reset audit.
