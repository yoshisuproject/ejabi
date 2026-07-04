# Changelog

All notable changes to Ejabi are documented here.

## Unreleased

- Added ANTLR-backed template parsing with escaped output, raw output, comments, and literal tag support.
- Added expression parsing and evaluation for literals, variables, property/index access, arithmetic, comparison, logical operators, and ternary expressions.
- Added `if`/`else` and `for` template control flow.
- Added public exception hierarchy rooted at `EjabiException`.
- Added Kotlin and Java API tests, Maven publication metadata, sources jar, Dokka-backed javadoc jar, and optional signing support.
- Added Gradle 10-compatible ANTLR package configuration.
- Added a `releaseCheck` Gradle task for CI and local release-readiness verification.
- Added Erubi compatibility notes and trim/literal tag behavior coverage.
