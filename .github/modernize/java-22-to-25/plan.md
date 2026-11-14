# Modernization Plan: Java 22 to Java 25 LTS

**Project**: Kvit (JavaFX desktop application)

## Technical Framework

- **Language**: Java 22, targeting Java 25 LTS
- **Framework**: JavaFX 22.0.2, with JavaFX 25 compatibility in scope
- **Build Tool**: Maven via the Maven Wrapper
- **Database**: Supabase PostgreSQL (no database changes planned)
- **Key Dependencies**: JavaFX Controls, Jackson Databind, JUnit 5
- **Application entry point**: `kz.kvit.App`

## Overview

Upgrade the Kvit desktop application from Java 22 to Java 25 LTS while preserving its existing Java/JavaFX stack, desktop behavior, and application features. Java 25 is the requested LTS target as of 2026-11-14. The plan covers compatibility across the JDK, JavaFX, Maven build/toolchain, and the application's launch and packaging/runtime configuration. Source changes are limited to those required for compatibility.

The work is sequenced to:

- Establish Java 25 availability for local development and any build/package workflows.
- Update and verify the Java and JavaFX build/runtime configuration for Java 25.
- Preserve existing application behavior and confirm compilation, tests, and desktop startup.
- Check dependency CVEs after the upgrade, limited to the resulting project dependency set.

## Migration Impact Summary

| Component | Current | Target | Scope |
|---|---|---|---|
| Java runtime/build target | Java 22 | Java 25 LTS | Upgrade and verify |
| JavaFX | JavaFX 22.0.2 | JavaFX 25 compatible | Compatibility/update as needed |
| Maven and launch/package setup | Existing Maven Wrapper | Java 25-capable | Adjust only if required |
| Desktop behavior and features | Existing Kvit application | Preserved | No feature changes |
| Database and web application | Existing Supabase/React | Unchanged | Out of scope |

## Scope and Constraints

- Retain Java, JavaFX, Maven, and the current desktop application architecture.
- Preserve existing user-visible behavior and functionality.
- Confirm JavaFX 25 works with Java 25 and update the JavaFX dependency only as needed for compatibility.
- Verify that the required JDK is available to developers and any existing build or packaging workflow; update toolchain and runtime/package configuration only as necessary.
- Make source changes only when required to compile or run on Java 25.
- Verify Maven build, unit tests, and desktop application startup/runtime behavior.
- Do not migrate to Azure or remediate unrelated cloud-readiness findings. Assessment findings for filesystem use, environment/system configuration, hardcoded URLs, and default credentials are out of scope unless a specific change is strictly required for the Java 25 upgrade.
- The post-upgrade security task is limited to CVE scanning/remediation of dependencies in the upgraded project; it is not a general security or cloud-readiness remediation.

## Phases and Dependencies

1. **Java 25 upgrade** — first and primary task. Check JDK/toolchain availability; align Maven compiler target, JavaFX compatibility, and relevant launch/package/runtime configuration; make only required source/build changes; run build, unit tests, and a desktop startup/runtime check. This task addresses assessment issue `azure-java-version-01000` (“Java Version Has Reached the End of Support”), which is confirmed by the current Java 22 target in `pom.xml`.
2. **Post-upgrade CVE check** — run after the upgrade so the scan covers the resulting dependency set. Remediate only relevant dependency CVEs within this plan's scope.

The security task depends on completion of the Java upgrade. No infrastructure, deployment, containerization, or separate integration-test task is planned. Build, unit-test, and desktop startup verification are part of the upgrade task.

## Assessment Findings

- **In scope**: `azure-java-version-01000`, Java version finding at `pom.xml:13`. The assessment reports JDK 22; the current Maven configuration independently confirms `maven.compiler.release` is `22` and `javafx.version` is `22.0.2`.
- **Out of scope**: Azure/cloud-readiness findings for system configuration, local filesystem use, hardcoded URLs, and the default/well-known password. They do not form part of a Java runtime upgrade.

## Open Questions & Questionnaire

- [x] Target runtime → Java 25 LTS, as explicitly requested (current authoritative evidence supplied by the user, dated 2026-11-14).
- [x] Environment/infrastructure provisioning → No; this is a code/build/runtime upgrade, with no Azure infrastructure scope.
- [x] Integration testing → No separate integration-test task; build, unit-test, and desktop startup/runtime verification are required within the upgrade task.
- [x] Deployment → No deployment; migration/upgrade only.
- [x] Security/CVE remediation → Include the required post-upgrade CVE check, narrowly scoped to the upgraded dependency set.
