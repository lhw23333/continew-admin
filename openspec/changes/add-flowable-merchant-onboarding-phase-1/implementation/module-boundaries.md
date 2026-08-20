# Module Boundary Baseline

## Completed Boundaries

| Module | Responsibility | Direct project dependencies | Flowable access |
|---|---|---|---|
| `continew-extension-workflow` | Project workflow contracts and Flowable adapter implementation | None | Allowed only under `workflow.internal.flowable` |
| `continew-merchant` | Agent, merchant master, KYC, review, and limit domains/application services | `continew-common` | Forbidden by ArchUnit |
| `continew-channel` | Channel ports, normalized DTOs, and transport adapters | None | Forbidden by ArchUnit |

The public packages are `top.continew.admin.workflow.api|dto`, `top.continew.admin.merchant.api|dto`, and `top.continew.admin.channel.api|dto`. Flowable implementation types belong under `top.continew.admin.workflow.internal.flowable` and must not cross a public contract.

The focused Maven test run executed three ArchUnit rules with zero failures. The server reactor also starts successfully with all three modules and Flowable on the isolated H2 integration profile.

## Dual-Database Verification

`MySqlApplicationIT` uses MySQL `8.4.0` and `PostgreSqlApplicationIT` uses PostgreSQL `16-alpine` through Testcontainers. Each profile selects its matching MyBatis database ID, pagination dialect, and Liquibase changelog.

Docker Desktop `4.87.0` with Engine `29.7.2` is installed through the WSL 2 backend. Testcontainers was upgraded from the Spring Boot-managed `1.19.8` baseline to `1.21.4` because Docker Engine 29 rejects the older Docker API used by `1.19.8`.

Both database profiles executed against real containers on August 20, 2026:

- MySQL `8.4.0`: `MySqlApplicationIT`, tests `1`, failures `0`, errors `0`, skipped `0`.
- PostgreSQL `16-alpine`: `PostgreSqlApplicationIT`, tests `1`, failures `0`, errors `0`, skipped `0`.

Each test started the complete ContiNew application with the merchant, channel, and workflow modules, applied the database-specific Liquibase changelog, and initialized Flowable against the container datasource.
