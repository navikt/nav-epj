---
applyTo: "**/*.kt", "**/*.kts", "**/build.gradle.kts", "**/settings.gradle.kts"
---

# Kotlin/Ktor Development Guidelines

## Application Structure

```
src/main/kotlin/no/nav/<app>/
├── Application.kt          # Entry point, wires plugins + modules
├── plugins/                # Global Ktor plugins (DI, auth, serialization, monitoring)
├── core/                   # Shared utilities (config, logging, db helpers)
│   ├── Environment.kt
│   ├── Logging.kt
│   └── db/                # Database utilities (Flyway, dbQuery)
└── <domain>/              # Feature modules (one per domain concept)
    └── <feature>/
        ├── <Feature>Routing.kt
        ├── <Feature>Service.kt
        ├── repository/
        │   ├── <Feature>Repository.kt      # Interface
        │   ├── <Feature>Table.kt           # Exposed table definition
        │   └── Stub<Feature>Repository.kt  # Local dev implementation
        └── <external-client>/              # External API clients
```

### Wiring

Global setup uses `Application.configure*()` extensions. Feature routes use `Route.configure*Routing()`:

```kotlin
// Application.kt
fun Application.module() {
    configureSerialization()
    configureSession()
    configureSecurity()
    configureRouting()
    configureFhirRouting()
}

// Feature routing within a parent route
fun Route.configurePatientRouting() { /* route handlers */ }
```

## Dependency Injection

Use Ktor's DI plugin (`ktor-server-di`). Register in `plugins/Dependencies.kt` or per-feature:

```kotlin
fun Application.configureDependencies() {
    dependencies {
        provide<HttpClient> { configureBaseHttpClient() }
        provide<Environment> { initializeEnvironment(environment.config) }
    }
}

// Environment-specific switching
fun Application.configureFeatureDependencies() {
    dynamicDependencies {
        local { provide(StubPersonClient::class) }
        cloud { provide(PersonCloudClient::class) }
    }
}

// Resolve via delegation
val service: FeatureService by dependencies
```

## Configuration

YAML config (`application.yaml`) with environment variables for secrets. Parse into a typed `Environment` class:

```kotlin
class Environment(
    val runtime: Runtime,
    val postgres: PostgresConfig,
    val auth: () -> Auth,         // Lazy - may not be needed in all envs
    val external: () -> ExternalApi,
)
```

## Serialization

- **Ktor endpoints**: `kotlinx.serialization` via `ContentNegotiation` with `Json { ignoreUnknownKeys = true }`
- **FHIR resources**: `FhirR4Json` from `com.google.fhir:fhir-model` - do NOT use kotlinx or Jackson for FHIR types
- **Exposed JSON columns**: `jsonb<T>(column, fhirJsonConfig)` using kotlinx Json config

Never mix serialization frameworks for the same type.

## Error Handling

Sealed interfaces for domain errors. Map to HTTP at the route boundary:

```kotlin
sealed interface CreateError {
    data object NotFound : CreateError
    data object ValidationFailed : CreateError
}

// In routes:
when (error) {
    is CreateError.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody("Not found"))
    is CreateError.ValidationFailed -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorBody("Invalid"))
}
```

- Never throw for expected business outcomes
- Use sealed types to enumerate failure modes
- Keep services pure; map errors at boundaries

## External API Clients

Interface + implementation pattern. DI switches based on environment:

```kotlin
interface PersonClient {
    suspend fun getPerson(ident: String): Result<Person>
}

class PersonCloudClient(httpClient: HttpClient, tokenClient: TokenClient, env: Environment) : PersonClient { ... }
class StubPersonClient : PersonClient { /* static test data */ }
```

## Database

- **Exposed R2DBC** with `dbQuery { }` wrapper (suspendTransaction on `Dispatchers.IO`)
- **Repository interface** + Stub implementation for local dev
- **Flyway** for migrations: `src/main/resources/db/migrations/V{N}__{description}.sql`
- Never modify existing migrations - always create new ones

## Authentication

- Named JWT providers via `install(Authentication) { jwt("provider-name") { ... } }`
- Protect routes with `authenticate("provider-name") { ... }`
- Service-to-service: Texas token exchange via `TokenClient`
- Local dev: OAuth2 stub with `configureOidcStub()`

## Monitoring & Observability

- **Health**: KHealth plugin (`/internal/health/alive`, `/internal/health/ready`)
- **Metrics**: Micrometer + Prometheus registry
- **Tracing**: CallId + CallLogging with MDC; OpenTelemetry `@WithSpan` on service methods
- **Logging**: StackWalker-based `logger()` factory; structured JSON in production via logback

## Testing

- **JUnit 5** lifecycle + **Kotest assertions** (`shouldBe`, `shouldThrow`) + **MockK** (`mockk`, `coEvery`)
- **Testcontainers** for integration tests requiring Postgres
- Use `testApplication { }` for route integration tests

```kotlin
@Test
fun `should return patient by id`() = testApplication {
    application { configureSerialization(); configureFeatureRoutes() }
    val response = createClient { install(ContentNegotiation) { json() } }
        .get("/fhir/Patient/patient-001")
    response.status shouldBe HttpStatusCode.OK
}
```

## Naming Conventions

- Package: `no.nav.helse` with feature sub-packages
- Classes: PascalCase, suffixed by role (`Service`, `Repository`, `Client`)
- Extension functions: `configure<What>()` on `Application`, `configure<What>Routing()` on `Route`
- Tests: `<Subject>Test`, methods in backticks (`should do X when Y`)
- **Language**: Code in English, domain terms in Norwegian (e.g. `getSykmeldinger`, `Behandler`, `Fastlege`)

## Code Style

- **ktfmt** via Spotless (kotlinlangStyle) + **detekt** for static analysis
- `sealed interface` for closed type hierarchies, `enum class` for small fixed sets, `data class` for DTOs
- Max 5 return points per function
- Wildcard imports allowed

## Background Jobs

Coroutine-based: extend a `BackgroundJob` abstraction, register on `ApplicationStarted`, cancel on shutdown.

## Boundaries

### ✅ Always

- Extension functions on `Application`/`Route` for wiring
- Interfaces for external clients + repository contracts
- Flyway for all schema changes
- Health checks (liveness + readiness)
- Structured logging (JSON in production)
- ktfmt/Spotless before committing

### ⚠️ Ask First

- Adding new dependencies to `build.gradle.kts`
- New database migration
- Modifying authentication config
- Adding background jobs

### 🚫 Never

- Skip Flyway migration versioning
- Bypass authentication on API routes
- Use `!!` without prior null check
- Block coroutines with synchronous I/O
- Commit secrets or credentials
- Modify existing Flyway migrations
