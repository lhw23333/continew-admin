# Flowable Compatibility Spike

## Result

Flowable `7.1.0` is the selected phase-one compatibility baseline for ContiNew Admin `4.1.0`.

| Component | Verified version |
|---|---|
| Java runtime/compiler | Temurin 17.0.20+8 |
| Maven | 3.9.11 |
| ContiNew Starter | 2.14.0 |
| Spring Boot | 3.3.12 |
| Flowable process starter artifact | 7.1.0 |
| Flowable engine version constant | 7.1.0.2 |
| Test database | H2 2.2.224 |

Flowable `7.2.0` was not selected because its published dependency baseline uses Spring Boot `3.5.4`, Spring Framework `6.2.10`, and Jackson `2.19.2`, which is outside the current ContiNew Boot 3.3.x line. Flowable `7.1.0` was published against Spring Boot `3.3.4`, Spring Framework `6.1.13`, and Jackson `2.17.2`, aligning with the current major/minor dependency line.

## Verified behavior

- Spring Boot application context starts on Java 17.
- Flowable process engine schema initializes.
- BPMN resource auto-deploys.
- A process instance starts with identifier-only variables.
- A candidate-group user task is created.
- The task completes and the runtime process ends.
- Historic process instance and end time are available.

## Reproduction

The ignored spike project is located at `.cache/flowable-compatibility`.

```powershell
$env:JAVA_HOME='D:\AI\continew-admin\.cache\tools\temurin17\jdk-17.0.20+8'
$env:Path="$env:JAVA_HOME\bin;D:\AI\grg-merchant-platform\.tools\maven\apache-maven-3.9.11\bin;$env:Path"
mvn -B -o -f .cache\flowable-compatibility\pom.xml `
  "-Dmaven.repo.local=D:\AI\continew-admin\.cache\m2" test
```

Observed test result: `1` test, `0` failures, `0` errors, `BUILD SUCCESS`.

## Important finding

The process starter also auto-configured Flowable IDM and Event Registry configurators and created their H2 tables. Phase-one code will not use Flowable IDM as an identity source, but dependency/schema impact must be evaluated in task 1.2 and task 8.1.

## Dependency convergence

The spike was extended with the same MyBatis-Plus starter used by ContiNew. The initial Enforcer run correctly detected Flowable MyBatis `3.5.16` and MyBatis-Plus MyBatis `3.5.19` as divergent paths. The project now pins `org.mybatis:mybatis` to `3.5.19` in root dependency management.

| Dependency line | Selected version | Upstream baseline |
|---|---|---|
| Spring Boot | 3.3.12 | Flowable 7.1.0: 3.3.4 |
| Spring Framework | 6.1.20 | Flowable 7.1.0: 6.1.13 |
| Jackson | 2.17.3 | Flowable 7.1.0: 2.17.2 |
| MyBatis | 3.5.19 | Flowable 7.1.0: 3.5.16 |
| MyBatis-Plus | 3.5.12 | ContiNew baseline |
| mybatis-spring | 3.0.4 | ContiNew baseline |
| SLF4J | 2.0.17 | Flowable 7.1.0: 2.0.16 |
| Liquibase | 4.27.0 | ContiNew baseline; Flowable runtime does not require Liquibase |

After pinning MyBatis `3.5.19`:

- Spring Boot context with Flowable and MyBatis-Plus passed.
- BPMN process execution test passed.
- Maven Enforcer `dependencyConvergence` passed.
- The root POM pins Flowable `7.1.0` and MyBatis `3.5.19` for future modules.

The Flowable IDM and Event Registry transitive modules remain present. Task 8.1 must either accept and migrate their tables or prove a supported exclusion/configuration that preserves process-engine behavior.
