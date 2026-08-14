# STATE

Project memory for `coach-planner-api` — the Kotlin backend for the
[coach-planner](../../coach-planner) React app.

## Decisions

### AD-101

- **Decision**: The stack is **Kotlin 2.3.21 + Spring Boot 4.1.0 + Spring Data JPA (Hibernate 7) + Flyway**, built with Gradle (Kotlin DSL), on **Java 21**.
- **Reason**: User selection during specification. Spring Boot 4.1 (released 2026-06-11) is the current OSS-supported line — 4.0's OSS support ends 2026-12-31 and 3.5's ended 2026-06-30, so starting on 4.1 buys the longest runway on a greenfield project with zero migration debt. Java 21 rather than the 17 minimum: it is the current LTS, and Boot 4.1 supports up to Java 26. **Kotlin version corrected during T1 execution**: [start.spring.io](https://start.spring.io)'s metadata for `bootVersion=4.1.0` pins the Kotlin Gradle plugin to **2.3.21**, not the 2.4.0 assumed at planning time from a general web search. Boot's own dependency-management BOM is the authoritative source for what that Boot line was tested against — deferring to it rather than forcing a newer, untested Kotlin version, since AD-101's own trade-off already flags plugin-compatibility risk as the biggest execution risk.
- **Trade-off**: Boot 4.x is the most disruptive Spring release since the `javax`→`jakarta` move — Jakarta EE 11, **Jackson 3**, Hibernate 7, Spring Security 7. Concretely this means the starter is `spring-boot-starter-webmvc` (not `-web`), `@MockBean`/`@SpyBean` are gone (use `@MockitoBean`), and virtual threads are on by default for the web thread pool. Most StackOverflow answers and tutorials still describe 3.x. Every third-party library must be checked for Boot 4 compatibility before being added — this is the single biggest execution risk in the plan. **Verified in T1**: the project skeleton was generated via start.spring.io (the authoritative source for current Boot 4.1 coordinates) rather than hand-written, confirming `spring-boot-starter-webmvc`, `tools.jackson.module:jackson-module-kotlin`, and `org.testcontainers:testcontainers-postgresql`/`org.testcontainers.postgresql.PostgreSQLContainer` (the v2 package, not `org.testcontainers.containers`) as the real coordinates.
- **Scope**: All backend work.
- **Date**: 2026-08-14 (Kotlin version corrected 2026-08-14 during T1)
- **Status**: active

### AD-102

- **Decision**: **PostgreSQL 18** is the database, run in Docker via `docker-compose.yml` for local development. MySQL was rejected.
- **Reason**: User delegated the choice. Three properties decide it: (1) `jsonb` stores `exercise.diagram` — the normalised-coordinate diagram model from the frontend's AD-015 — as a queryable, validated column rather than a text blob; (2) partial and expression indexes (`UNIQUE (owner_id, lower(name))`) implement the case-insensitive reference-list uniqueness the frontend enforces in JavaScript today, at the database level; (3) real `CHECK` constraints are enforced, where MySQL only gained them in 8.0.16 and they interact badly with some tooling.
- **Trade-off**: Slightly heavier local footprint than MySQL, and `jsonb` tempts schema-by-JSON — the diagram column is the *only* sanctioned JSON column and new entities must earn real tables.
- **Scope**: `docker-compose.yml`, all Flyway migrations, all JPA mappings.
- **Date**: 2026-08-14
- **Status**: active

### AD-103

- **Decision**: The API introduces **real authentication with per-user data ownership**. A `users` table, JWT access tokens with rotating refresh tokens, and an `owner_id` on every root entity. Every read and write is scoped to the authenticated caller.
- **Reason**: User selection during specification. The frontend's `AuthContext` is an acknowledged mock (its AD-011/AD-018 both say it is "replaced wholesale when a real backend arrives"), and no frontend entity has an owner — teams, trainings and games are global. Ownership is the one property that cannot be retrofitted cheaply: adding `owner_id` later means backfilling every row with no correct answer for who owns it.
- **Trade-off**: Roughly a third of the backend's task count is auth that delivers no visible feature to a single-coach user. Accepted because the alternative is a rewrite. Registration is open (no invite/approval flow) — this is a personal tool, not a SaaS product.
- **Scope**: All entities, all endpoints, `V1__init.sql`.
- **Date**: 2026-08-14
- **Status**: active

### AD-104

- **Decision**: The frontend's **AD-010 stands**: `games.competition` and `games.opponent` remain **name strings**, not foreign keys. `competitions` and `opponents` stay independent reference lists whose rename cascades to matching games explicitly, inside one transaction.
- **Reason**: User selection during specification. The backend's job is to serve the app that exists. Converting to real FKs would reverse a standing frontend decision, change every game read and write path in React, and require a migration with no correct answer for games whose competition was deleted.
- **Trade-off**: No referential integrity between games and the reference lists — the database cannot stop `games.competition` drifting from `competitions.name`. The cascade is therefore application logic that **must** be transactional and **must** be tested, because nothing below it will catch a partial rename. A deleted competition deliberately leaves its name on historical games (the fixture happened). Revisit if per-competition standings are ever built.
- **Scope**: `competitions`, `opponents`, `games`, and their rename endpoints.
- **Date**: 2026-08-14
- **Status**: active

### AD-105

- **Decision**: Entity ids are **UUIDs** (`uuid` column type, `UUIDv7` generated application-side), never database sequences.
- **Reason**: The frontend's AD-003 already made ids opaque strings via `crypto.randomUUID()`, and every frontend lookup is `find(x => x.id === id)` against a string. Keeping UUIDs means the API returns exactly what the frontend's stored records already look like, so a swap needs no id translation layer. UUIDv7 over v4 because it is time-ordered, which keeps B-tree index inserts sequential instead of scattering them.
- **Trade-off**: 16 bytes per key instead of 4-8, and ids are unreadable in logs. The frontend already solved the human-readable-identifier problem separately (its AD-006 computes a training's display number on read) so nothing in the product needs a readable id.
- **Scope**: Every table's primary key.
- **Date**: 2026-08-14
- **Status**: active

### AD-106

- **Decision**: `ratings` uses **two nullable FK columns** (`training_id`, `game_id`) with a `CHECK` constraint that exactly one is set, rather than the frontend's polymorphic `(eventType, eventId)` pair. The API still exposes `eventType`/`eventId` in its JSON, derived in the mapping layer.
- **Reason**: The frontend's polymorphic pair cannot carry a foreign key, which is why `ratingService.removeByEvent` exists as a hand-wired cascade called from two other services. In Postgres, two nullable FKs with `ON DELETE CASCADE` make deleting a game or training remove its ratings *in the database*, so the cascade cannot be forgotten at a third call site.
- **Trade-off**: The storage shape and the wire shape differ, so there is a mapping function to keep correct — and a `CHECK ((training_id IS NULL) != (game_id IS NULL))` that must be tested directly, since no application path should ever violate it. Accepted: a constraint that is hard to violate beats a cascade that is easy to forget.
- **Scope**: `ratings` table, `RatingMapper`, rating endpoints.
- **Date**: 2026-08-14
- **Status**: active

### AD-107

- **Decision**: Deleting a team **nulls** `team_id` on its trainings and games (`ON DELETE SET NULL`), and **cascades** to its players (and through them to cards and ratings).
- **Reason**: This is exactly what the frontend already does, and the asymmetry is deliberate there: `teamService.delete` cascades players' cards and ratings but leaves games untouched, because `gameService.getUnassigned` treats a dangling `teamId` as *reassignable*, not orphaned. A played fixture is a historical fact that outlives the squad record; a player is not.
- **Trade-off**: Trainings and games can exist with no team, so every read path must tolerate `teamId: null` and the "unassigned" filters are load-bearing, not a convenience. Enforced by FK actions in the schema rather than service code, so it holds for every write path including manual SQL.
- **Scope**: `V1__init.sql` FK definitions, team-delete endpoint, unassigned filters.
- **Date**: 2026-08-14
- **Status**: active

### AD-108

- **Decision**: **Derived values stay derived.** The API never stores `points`, `goalDifference`, game `outcome`, a training's display `number`, or rating averages. `number` and the standings table are computed on read by the server; outcome and rating aggregates stay in the frontend's existing pure functions.
- **Reason**: Mirrors the frontend's AD-006 and `lib/gameResult.js`/`lib/standings.js`, which already compute all of these and are covered by its test suite. Storing them would create two sources of truth across the network boundary — the worst place to have one.
- **Trade-off**: `GET /trainings` costs a per-team ordering pass to assign `number`, so it cannot be served straight from a single indexed query. Accepted; the dataset is one coach's teams.
- **Scope**: Training list endpoints, standings endpoints, game serialization.
- **Date**: 2026-08-14
- **Status**: active

### AD-109

- **Decision**: All errors return **RFC 9457 `application/problem+json`** from one `@RestControllerAdvice`. No controller builds its own error body.
- **Reason**: The frontend already has typed `NotFoundError`/`ValidationError` in `src/lib/errors.js` and call sites that branch on them. A single machine-readable envelope with a stable `type` field lets those classes be reconstructed from the response instead of parsing message strings. Spring Boot 4 supports `ProblemDetail` natively.
- **Trade-off**: More verbose than a bare `{"error": "..."}`. Field-level validation errors need a non-standard `errors` extension member, which is a documented deviation rather than an accident.
- **Scope**: Every endpoint.
- **Date**: 2026-08-14
- **Status**: active

### AD-110

- **Decision**: Integration tests run against **real PostgreSQL via Testcontainers**, using Spring Boot's built-in `@ServiceConnection` support. No H2, no in-memory substitute.
- **Reason**: Half the decisions above are enforced *by the database* — `CHECK` constraints (AD-106), FK actions (AD-107), expression-based unique indexes (AD-102), `jsonb`. An H2 test suite would pass while every one of those went untested, which is the failure mode the frontend's `.specs/LESSONS.md` records repeatedly: a green suite that proves nothing.
- **Trade-off**: Tests need a running Docker daemon and are slower than in-memory. **Risk flagged:** Testcontainers v2 with Spring Boot 4 has known compatibility gaps in some third-party modules; the Postgres module plus `spring-boot-testcontainers:4.1.0` is the verified-supported combination and the first task must prove it boots before anything else is built on it.
- **Scope**: All integration tests, `build.gradle.kts`.
- **Date**: 2026-08-14
- **Status**: active

## Handoff

- **Feature**: `00-backend-mvp` — **Phase 0 done and verified, 44 tasks remain.** Spec/design/tasks written 2026-08-14; Phase 0 (T1–T5) implemented and committed the same day.
- **Phase / Task**: Phase 0 complete (T1–T5, all ✅ in `tasks.md`). Phase 1 (`T6`–`T10`, schema & persistence) is next and not started.
- **Completed**: T1 (Gradle/Kotlin 2.3.21/Boot 4.1.0 skeleton, Testcontainers spike — `d8f450d`) → T2 (Docker Compose for Postgres 18, `.env.example` — `b2b9605`) → T3 (Flyway wiring, dev/test profiles, no-op-on-second-boot proof — `195aeb9`) → T4 (Jackson 3 config: nulls retained, unknown fields ignored, ISO-8601 instants — `edf7729`) → T5 (actuator health/info exposure, structured JSON logging with correlation id, fail-fast on unreachable DB — `dec25a4`). Each task's own commit message documents the real API/behavior discoveries made while implementing it (see below) — read those before touching the same area again rather than re-deriving.
- **In-progress** (file:line): none — Phase 0 fully closed out, gate green and re-run 3x with no flakiness.
- **Corrections made during Phase 0 execution** (STATE.md's Decisions section already reflects these where load-bearing; noted here for anyone resuming):
  - AD-101: Kotlin pinned to **2.3.21**, not 2.4.0 — Boot 4.1.0's own dependency-management BOM (verified via start.spring.io) pins this, not a general web search.
  - `spring-boot-autoconfigure` no longer bundles most autoconfiguration — Boot 4 split it per technology (`spring-boot-jackson`, `spring-boot-webmvc`, `spring-boot-jpa`, `spring-boot-security`, etc.). Expect this pattern everywhere in later phases; verify actual jar contents before writing config code, don't assume a class's old home.
  - Jackson 3: customizer is `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer` over `tools.jackson.databind.json.JsonMapper.Builder`, not `Jackson2ObjectMapperBuilderCustomizer`/`ObjectMapper`. `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` is gone — use `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS` (a `DatatypeFeature`).
  - Postgres 18's official image moved its expected volume mount from `/var/lib/postgresql/data` to `/var/lib/postgresql` — the old path makes the container refuse to start. Already fixed in `docker-compose.yml` and `design.md`.
  - `TestRestTemplate` moved to `org.springframework.boot.resttestclient.TestRestTemplate` and needs `@AutoConfigureTestRestTemplate` explicitly; it also pulls in a `RestTemplateBuilder` class not on this project's classpath, so **use `MockMvc.perform()` for HTTP-layer tests going forward**, not `TestRestTemplate`, until/unless `spring-boot-restclient` is added deliberately.
  - `@Component`-registered `ApplicationListener`s are not reliably wired in time to catch failures during the *first* bean-creation phase (e.g., datasource/Flyway init) — use `META-INF/spring.factories` bootstrap-time registration for anything that must observe early startup failure.
  - `HttpSecurity` only exists as a bean in a web application context — any `@Configuration` class defining a `SecurityFilterChain` needs `@ConditionalOnWebApplication`, or non-web test harnesses (`SpringApplicationBuilder(...).web(WebApplicationType.NONE)`) break with an unrelated bean error.
  - Don't attach a Logback `ListAppender` to a logger *before* calling `SpringApplicationBuilder.run()` in the same test — Boot's `LoggingSystem` reinitializes Logback as part of that call and can silently detach it (order-dependent flakiness, not caught by a single run). Unit-test log-emitting classes in isolation instead (see `StartupFailureLoggerTest`).
- **Next step**: Phase 1 (`T6`–`T10`) — write `V1__init.sql` whole (the complete schema from `design.md`), then the JPA entities and the cascade/constraint test suite. `T6` is the highest-leverage task in this phase: get the schema right once, since Flyway checksums make it costly to edit after real data exists (not a concern yet — nothing has shipped — but the pattern should hold from here on).
- **Blockers**: none. Requires Docker running locally (already confirmed working: Postgres 18 healthy via `docker compose up -d db`, Testcontainers-based tests green).
- **Uncommitted files**: none — working tree clean as of `dec25a4`.
- **Branch**: `main`. `coach-planner-api` is its own git repository (confirmed during Phase 0 — not nested in the frontend repo's history). All Phase 0 commits are directly on `main`; no feature branch was used for this round.
- **Open items carried forward**: (1) rival standings rows have no `team_id` in the frontend model, so a coach with two teams shares one league table — recorded as an assumption in `spec.md`, not resolved; (2) the frontend rewiring (replacing its 8 services with `fetch`) is explicitly out of scope this round and needs its own feature in the frontend repo; (3) no data import path from existing `localStorage` records — deliberately deferred; (4) `SecurityConfig.kt` is a deliberate placeholder (permits only `/actuator/health` and `/actuator/info`, denies everything else) — Phase 3 (T14–T21) must replace it wholesale with the real JWT chain, not extend it incrementally.
