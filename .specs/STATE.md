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

- **Feature**: `00-backend-mvp` — **Phases 0–5 done and verified, 18 tasks remain.** Spec/design/tasks written 2026-08-14; Phases 0–4 (T1–T26) implemented and committed 2026-08-14; Phase 5 (T27–T31) implemented and committed 2026-08-17.
- **Phase / Task**: Phases 0–5 complete (T1–T31, all ✅ in `tasks.md`). Phase 6 (`T32`–`T37`, games & standings) is next.
- **Completed — Phase 0**: T1 (Gradle/Kotlin 2.3.21/Boot 4.1.0 skeleton, Testcontainers spike — `d8f450d`) → T2 (Docker Compose for Postgres 18, `.env.example` — `b2b9605`) → T3 (Flyway wiring, dev/test profiles, no-op-on-second-boot proof — `195aeb9`) → T4 (Jackson 3 config: nulls retained, unknown fields ignored, ISO-8601 instants — `edf7729`) → T5 (actuator health/info exposure, structured JSON logging with correlation id, fail-fast on unreachable DB — `dec25a4`).
- **Completed — Phase 1**: T6 (the complete `V1__init.sql` in one pass; `SchemaIT` proves every table/CHECK/FK action against real Postgres via catalog queries — `a1a26a9`) → T7 (User/RefreshToken/Team/Player entities, hand-rolled UUIDv7 in `common/Ids.kt` — `935a364`) → T8 (Training/Exercise/Game/RivalRow entities, `Exercise.diagram` as `Map<String,Any?>?` jsonb — `bfbcf52`) → T9 (Card/Rating/Competition/Opponent entities, AD-106's two-nullable-FK rating model — `0542d25`) → T10 (`ConstraintsIT`/`CascadeIT` safety net, deletes via raw JDBC to prove DB-level enforcement, not Hibernate's own cascade config — `a4a83e9`).
- **Completed — Phase 2**: T11 (`common/errors.kt` — `NotFoundException`/`ValidationException`/`ConflictException`, each carrying an overridable `type` slug; `ApiExceptionHandler` as one `@RestControllerAdvice` building RFC 9457 `ProblemDetail` — `8237bf6`) → T12 (`MethodArgumentNotValidException` → 400 with a field-keyed `errors` extension member via `ProblemDetail.setProperty` — `ee811cb`) → T13 (`DataAccessResourceFailureException`→503, `OptimisticLockingFailureException`→409, `MethodArgumentTypeMismatchException`→400, catch-all `Exception`→500 with nothing leaked — `90df632`).
- **Completed — Phase 3**: T14 (registration: bcrypt cost 12, citext-based duplicate-email 409, real `JwtService`/`RefreshTokenService` issuers so registration returns genuine tokens — `7ea27a1`) → T15 (`JwtDecoder` bean + `.oauth2ResourceServer` wiring; round-trip/expired/wrong-key tests through the real filter chain — `39f12fe`) → T16 (login with timing-safe invalid-credentials handling — byte-identical 401 bodies for wrong-password vs unknown-email — `f9fdb0d`) → T17 (refresh rotation + reuse-detection mass revocation, `TransactionTemplate(REQUIRES_NEW)` fix for a real rollback bug — `d2039e8`) → T18 (the real stateless resource-server chain: CORS, `ProblemJsonAuthenticationEntryPoint` distinguishing `token-expired` from other failures, `ProtectedPathsIT`'s parameterised sweep — `0af22e2`) → T19 (logout + `GET`/`PATCH /users/me` — `fa629f1`) → T20 (`PUT /users/me/password` with hash-untouched-on-failure proof — `ac8d3be`) → T21 (`common/CurrentUser.kt` resolver + `common/OwnedRepository.kt` retrofit across all six owned repositories + `OwnershipIsolationIT` — `87b665f`).
- **Completed — Phase 4**: T22 (Team CRUD — `GET`/`POST /teams`, `GET`/`PATCH /teams/{id}`; `@Transactional(readOnly=true)` reads so `Team.players` lazy-loads inside the mapping step; PATCH proven to touch only supplied fields via a repository-seeded player — `2f34d43`) → T23 (team delete relying purely on schema FK actions, no manual cascade — `71b7b7a`) → T24 (player create/update, stricter 17-code position enum, `age`/`shirtNumber`/stat bounds on both create and PATCH; found and fixed a real gap — an unrecognised enum value threw `HttpMessageNotReadableException`, which the existing catch-all would have surfaced as 500 instead of 400 — `e92b33b`) → T25 (player read/delete, cross-team `playerId` → 404 — `66c4b50`) → T26 (`OwnershipIsolationIT` extended from repository-only to HTTP-level: user B gets 404, never 403, on every team and player verb — `00ec7e1`).
- **Completed — Phase 5**: T27 (`TrainingNumbering.numbersById` — pure port of the frontend's `numberTrainings`; tie-break by `id.toString()`, not `UUID.compareTo`, to match the frontend's string comparison exactly — `d86b541`) → T28 (`POST`/`PATCH /trainings` — exercises replaced wholesale when the key is present, untouched when absent, via Kotlin's nullable-with-default carrying that distinction natively; a body `teamId` owned by another user is `400 unknown-team`, not `404` — `4667c06`) → T29 (`GET /trainings` with `?teamId=`/`?assigned=false`, `GET /trainings/{id}`; numbers always computed over the owner's whole history and filtered after, proven directly by comparing filtered vs unfiltered numbers in the same test — `a392da4`) → T30 (`DiagramValidator` — shape-count/byte-size limits reject, coordinate clamping mirrors the frontend's `clampToPitch`; both a pure unit-test file and an HTTP round-trip IT, since the JSON-double-encoding risk lives at the mapping layer not the pure logic — `d88cef9`) → T31 (`DELETE /trainings/{id}` relying purely on JPA `orphanRemoval` + the schema's `ON DELETE CASCADE` on `ratings.training_id`; `OwnershipIsolationIT` extended to the training HTTP paths — `3106008`) → fix commit `d0789bf` (two Verifier-flagged gaps closed: invalid-`day` → `400` edge case, and the diagram byte-limit re-proven through the real HTTP→jsonb round trip, not just the unit level).
- Each task's own commit message documents the real discovery made while implementing it — read those before touching the same area again rather than re-deriving. **149 tests total, all green, stable across repeated runs.**
- **Verified**: Phase 5 passed a fresh-Verifier pass (author ≠ verifier) — spec-anchored coverage (12/12 ACs matched spec-defined outcomes) + a 5-mutation discrimination sensor (all killed) on the first pass, plus a targeted re-check of the two fix-commit gaps on the second pass. Full report: `.specs/features/00-backend-mvp/validation.md`.
- **In-progress** (file:line): none — Phase 5 fully closed out and verified.
- **Corrections made during Phase 0 execution** (see git history — Kotlin 2.3.21 pin, Boot 4's per-technology autoconfigure split, Jackson 3 renames, Postgres 18's volume-mount change, `TestRestTemplate`/`HttpSecurity`/Logback gotchas). Still load-bearing.
- **Corrections made during Phase 1 execution** (see git history — `citext`/`smallint` exact-type requirements, `@OrderColumn`'s owning-side requirement, JSON `JdbcType` double-encoding risk, native-enum case sensitivity, testing DB-enforced cascades via raw JDBC not `repository.delete()`). Still load-bearing.
- **Corrections made during Phase 2 execution**: none — every task went green on the first attempt.
- **Corrections made during Phase 3 execution** (five real findings — Kotlin nullable-platform-type surprises on `PasswordEncoder.encode()`/`Jwt.getSubject()`; `@ConditionalOnWebApplication`'s class-vs-method scope, hit twice; a genuine transaction-rollback security bug in reuse detection, fixed with `TransactionTemplate(REQUIRES_NEW)`; `OAuth2ResourceServerConfigurer`'s own entry-point precedence over `.exceptionHandling`; Kotlin's nested block comments breaking a KDoc that mentioned `/api/v1/**`). Still load-bearing — see prior handoff in git history for the full write-up.
- **Corrections made during Phase 4 execution**: one real gap, no framework surprises this time — an unrecognised enum value in a request body (`position: "STRIKER"`) throws `HttpMessageNotReadableException` from the message-converter layer, before the controller or `@Valid` ever run. Being an `Exception` subtype, the existing catch-all handler would have swallowed it into a 500. Fixed with a dedicated `@ExceptionHandler(HttpMessageNotReadableException::class)` → 400, plus a regression test in `ApiExceptionHandlerIT`. Worth remembering: **any new enum-typed (or otherwise type-coercible) request field should be checked against this path**, not just against `@Valid`'s bean-validation path — they fail at different layers.
- **Corrections made during Phase 5 execution**: no framework surprises; the Verifier's fresh-eyes pass caught one real spec-coverage gap the author missed while implementing — spec.md's "invalid `day` → `400`" edge case had an implementation (the Phase 2 generic `HttpMessageNotReadableException` handler) but zero test proving it for the training endpoint specifically. Closed in `d0789bf`. Worth remembering: **an edge case "covered" by a generic handler from an earlier phase still needs its own test at the point the spec calls it out**, or it counts as unverified under evidence-or-zero.
- **Next step**: Phase 6 (`T32`–`T37`, games & standings) — game create/update (scores forced null on create, rejected on `PATCH`), reads/filters (`status=scheduled|played`, the **`0-0` game must classify as `played`** null-vs-zero trap flagged as this phase's highest risk), standings computation ported from the frontend's `computeOurRow`/`sortStandings`. See tasks.md T32–T37 for exact files/tests.
- **Blockers**: none. Docker running locally, Postgres 18 healthy, all Testcontainers-based tests green.
- **Uncommitted files**: none — working tree clean as of `d0789bf`.
- **Branch**: `main`. All commits directly on `main`; no feature branch used for this round.
- **Open items carried forward**: (1) rival standings rows have no `team_id` in the frontend model, so a coach with two teams shares one league table — recorded as an assumption in `spec.md`, not resolved; (2) the frontend rewiring (replacing its 8 services with `fetch`) is explicitly out of scope this round and needs its own feature in the frontend repo; (3) no data import path from existing `localStorage` records — deliberately deferred; (4) no standalone `ExerciseRepository` yet — deferred to P11/T48 if/when granular exercise endpoints are built; (5) PATCH endpoints across the API (Team, Player, and future entities) treat an absent field as "don't touch" with no way to explicitly clear a nullable field to null — not full JSON-merge-patch semantics; noted as a simplification in T22, not silently built in, and not yet requested by any spec AC.
