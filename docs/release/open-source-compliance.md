# Open-Source Compliance and SBOM Release Gate

## Scope

This gate applies to every backend release candidate that contains the phase-one merchant onboarding capability. It covers the Maven reactor, Flowable, ContiNew Starter, and all resolved transitive dependencies. Frontend dependencies require a separate package-manager SBOM before the first frontend onboarding release.

## Declared Baseline

| Component | Selected version | Upstream declaration | Project handling |
|---|---:|---|---|
| ContiNew Admin project code | 4.1.0 | Apache-2.0 | Retain the repository `LICENSE` |
| Flowable process starter | 7.1.0 | Apache-2.0 | Retain license/notices and mark source modifications |
| ContiNew Starter | 2.14.0 | GNU LGPL; upstream POM does not state an SPDX version | Preserve exact release license, notices, corresponding-source rights, and relinking/replacement rights |

The root [THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md) records the current obligations and must ship with release documentation. It must be updated when the selected Flowable or ContiNew Starter version changes.

## Generate the Backend SBOM

Use the same JDK, Maven settings, active profiles, dependency mirrors, and revision as the release build. The `sbom` profile binds CycloneDX aggregate generation to `package`:

```powershell
mvn -B -Psbom -DskipTests package
```

Expected root outputs:

```text
target/sbom/continew-admin-4.1.0.json
target/sbom/continew-admin-4.1.0.xml
```

The generated files are build artifacts rather than source files. CI must retain both files with the immutable release candidate and publish them beside the released binaries. Do not reuse an SBOM from an earlier commit or generate it from dependency-management declarations alone.

## Review Checklist

1. Confirm the SBOM metadata component, Git commit, release version, build profile, JDK, and Maven version match the candidate.
2. Confirm every packaged Maven component appears with group, artifact, version, package URL, hashes where available, and declared license data.
3. Confirm `org.flowable:flowable-spring-boot-starter-process:7.1.0` and every actually packaged Flowable transitive module appear after the workflow module is introduced.
4. Confirm all `top.continew.starter:*` components resolve to the approved `2.14.0` line unless a reviewed exception is recorded.
5. Investigate missing, unknown, custom, copyleft, source-available, or conflicting license declarations before release.
6. Compare component/version changes against the previously approved SBOM and repeat legal/security review for additions or license changes.
7. Archive the JSON/XML SBOM, dependency vulnerability report, this checklist decision, exact third-party license/notice files, and corresponding-source location with the release record.

## LGPL Distribution Record

For each externally distributed candidate, record:

- the ContiNew Starter source tag/commit matching every distributed `top.continew.starter:*` binary;
- the exact upstream LGPL text and copyright/notice files;
- whether any Starter source was modified, with patches and build instructions when applicable;
- the corresponding-source delivery URL or other compliant offer and its retention period;
- confirmation that packaging, signing, access controls, and end-user terms do not block replacement/relinking or license-required debugging.

The upstream `2.14.0` POM names GNU LGPL but does not identify an SPDX version. The release owner must resolve that ambiguity from the exact source release and obtain legal approval before external distribution. This is a distribution gate, not permission to guess a license version.

## Failure Policy

Do not publish a release when the SBOM is missing/stale, a packaged dependency is absent from it, license data cannot be reconciled, required notices/source are unavailable, or legal/security review rejects a component. Rebuild from the same candidate after correcting the dependency or documentation; never hand-edit an SBOM to make a gate pass.
