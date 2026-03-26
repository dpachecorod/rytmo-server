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

## API Documentation

Every endpoint must have an `@APIResponse` annotation with:
- `responseCode` — the success HTTP status code (e.g. `"200"`, `"201"`)
- `description` — a short description of what the response represents
- `content` with a `Schema` pointing to the exact response model class (use `SchemaType.ARRAY` for list responses)

Example:
```kotlin
@APIResponse(
    responseCode = "200",
    description = "Current customer",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = Customer::class))],
)
```

For array responses:
```kotlin
@APIResponse(
    responseCode = "200",
    description = "List of items",
    content = [Content(mediaType = "application/json", schema = Schema(type = SchemaType.ARRAY, implementation = ItemResponse::class))],
)
```

## Code Style

Spotless enforces formatting with ktfmt and ktlint. Formatting is checked on build.
