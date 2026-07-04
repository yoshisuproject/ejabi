# Release Guide

This project publishes JVM artifacts with Gradle's `maven-publish` plugin. A release build produces the main jar, sources jar, javadoc jar, POM metadata, and optional OpenPGP signatures.

## Prerequisites

- Java 17
- A clean Git working tree
- A non-SNAPSHOT version in `build.gradle.kts`
- A remote Maven repository URL and credentials
- OpenPGP signing key material when publishing to Maven Central

## Local Verification

Run the full release preparation command before publishing:

```bash
./gradlew clean releaseCheck --warning-mode all
```

`releaseCheck` builds all modules, publishes them to Maven local, and verifies that each module's POM exists in the local Maven cache.

Inspect the local POM when changing dependencies or metadata:

```bash
~/.m2/repository/com/yoshisuproject/ejabi/<version>/ejabi-<version>.pom
```

## Signing

Signing is optional for local builds. It is enabled when these Gradle properties are present:

- `signingKey`: ASCII-armored private key
- `signingPassword`: private key passphrase
- `signingKeyId`: optional key id

In CI, pass them as environment variables using Gradle's `ORG_GRADLE_PROJECT_` convention, such as `ORG_GRADLE_PROJECT_signingKey`.

## Remote Publishing

Remote publishing is disabled unless `releaseRepositoryUrl` is provided. Configure credentials through Gradle properties:

```bash
./gradlew publish \
  -PreleaseRepositoryUrl=https://example.invalid/maven/releases \
  -PreleaseRepositoryUsername="$MAVEN_USERNAME" \
  -PreleaseRepositoryPassword="$MAVEN_PASSWORD"
```

For Maven Central, use the repository URL and token credentials from the current Central Portal account. Do not commit repository credentials or signing keys.
