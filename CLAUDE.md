# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Development (hot reload at http://localhost:8080)
./gradlew quarkusDev

# Build
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.rytmo.server.GreetingResourceTest"

# Run a single test method
./gradlew test --tests "com.rytmo.server.GreetingResourceTest.testHelloEndpoint"

# Build native executable
./gradlew build -Dquarkus.native.enabled=true
```

## Architecture

**Multi-module Gradle project** using Quarkus (Kotlin) with three modules:

- **server/** - REST API endpoints and authentication filters
- **library/** - Shared business logic, JWT verification, and authorization
- **models/** - Data transfer objects

**Key packages:**
- `com.rytmo.server.auth.filters.PrivyAuthFilter` - JWT authentication filter
- `com.rytmo.library.authorizers.PrivyAuthorizer` - ES256 JWT verification against Privy JWKS
- `com.rytmo.library.mappers` - MapStruct mappers for JWT claims

## Authentication

Uses Privy for Web3 authentication with ES256 JWT tokens:

1. Protect endpoints with `@PrivyProtected` annotation
2. `PrivyAuthFilter` intercepts requests and verifies JWT via `PrivyAuthorizer`
3. Valid tokens produce an `AuthorizedUser` object accessible in the request

Environment variables required in `server/.env`:
- `privy.app-id`
- `privy.app-secret`

## Testing

- 90% minimum code coverage enforced via JaCoCo
- Use `@TestProfile(PrivyTestProfile::class)` for tests requiring JWT authentication
- `AccessTokenUtil` generates valid test tokens with mock EC256 keys

## Code Style

Spotless enforces formatting with ktfmt and ktlint. Formatting is checked on build.
