# Coach Planner API — Tasks

**Spec:** `spec.md` · **Design:** `design.md` · **Decisions:** `../../STATE.md`

**49 atomic tasks across 10 phases.** One commit per task. Every task's gate is
`./gradlew build` (compile + lint + full test suite) green — self-assessment
never closes a task.

**Standing rules for every task**

- Tests derive from the spec's ACs and assert **spec-defined outcomes**, never
  what the implementation happens to do.
- Never weaken, skip, or delete a test to make a gate pass.
- Requires Docker running (Testcontainers, AD-110).
- After T49, a fresh **Verifier** runs automatically — author ≠ verifier.

---

## Phase 0 — Foundation & Docker (5 tasks) · `INFRA-01…06`

> **T1 is a risk spike.** AD-110 flags Boot 4.1 × Testcontainers v2 as the
> plan's biggest unverified assumption. It is proven here, before any domain
> code depends on it.

### T1 — Gradle skeleton + Testcontainers spike ✅ done
- **Files:** `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `src/main/kotlin/com/coachplanner/api/CoachPlannerApplication.kt`, `src/test/kotlin/.../SmokeIT.kt`
- **Do:** Kotlin 2.4 / Boot 4.1 / Java 21 toolchain. Starters: `webmvc` (**not** `web`), `data-jpa`, `validation`, `security`, `actuator`. Test: `spring-boot-starter-test`, `spring-boot-testcontainers:4.1.0`, Testcontainers `postgresql`.
- **Test:** `SmokeIT` boots the context against a `@ServiceConnection` `PostgreSQLContainer("postgres:18-alpine")` and asserts a live JDBC connection.
- **Done when:** `./gradlew test` green. **If the combination does not boot, STOP and report** — do not work around it silently; the fallbacks are in `design.md`'s risk table and the choice is the user's.
- **Depends on:** —

### T2 — Docker Compose for PostgreSQL 18 ✅ done
- **Files:** `docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`
- **Do:** The `db` service exactly as in `design.md` — named volume, `pg_isready` healthcheck, `POSTGRES_PASSWORD` with `:?` so a missing `.env` fails loudly. `.env` gitignored.
- **Test:** Manual, recorded in the commit message: `docker compose up -d db` → healthy within 30s; `docker compose down && up -d db` → data survives.
- **Done when:** A clean clone plus `cp .env.example .env` yields a healthy database. `AC INFRA-01`
- **Depends on:** —

### T3 — Flyway wiring + config profiles ✅ done
- **Files:** `src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`, `db/migration/V1__init.sql` (empty placeholder)
- **Do:** Flyway on, `ddl-auto: validate` (**never** `update` — Flyway owns the schema), datasource from env vars with dev defaults matching `.env.example`.
- **Test:** Context loads with an empty migration; `ddl-auto: validate` is asserted present by a config test.
- **Done when:** Migration runs on a fresh container and is a no-op on a second boot. `AC INFRA-02, INFRA-03`
- **Depends on:** T1, T2

### T4 — Jackson 3 configuration + DTO round-trip ✅ done
- **Files:** `config/JacksonConfig.kt`, `src/test/kotlin/.../JsonMappingTest.kt`
- **Do:** Kotlin module registered, `Instant` as ISO-8601 UTC, unknown properties **ignored**, nulls **retained** (`usScore: null` must serialize — omitting it breaks the frontend's `hasResult`).
- **Test:** Round-trip a DTO with a null field, a nullable `Instant`, and an unknown incoming property. Assert the null survives serialization and the unknown field is ignored, not rejected.
- **Done when:** Green. **Verify the Jackson 3 Kotlin-module coordinate against current docs — do not copy a 3.x-era coordinate.** `Edge cases: unknown JSON fields`
- **Depends on:** T1

### T5 — Actuator, structured logging, fail-fast ✅ done
- **Files:** `application.yml`, `config/LoggingConfig.kt` or `logback-spring.xml`
- **Do:** Expose `health` (with `db` component) and `info` only. JSON request logging with a per-request correlation id. Fail fast on an unreachable database, logging the JDBC URL (never the password).
- **Test:** `/actuator/health` returns `200` + `status: UP` + a `db` component; a context test with a bogus JDBC URL asserts startup failure rather than a degraded start.
- **Done when:** Green. `AC INFRA-04, INFRA-05, INFRA-06`
- **Depends on:** T3

---

## Phase 1 — Schema & persistence (5 tasks) · `DATA-01…06`

> `V1__init.sql` is written **once, whole** (T6). Flyway checksums make an
> incrementally-edited applied migration a broken migration.

### T6 — `V1__init.sql`: the complete schema ✅ done
- **Files:** `src/main/resources/db/migration/V1__init.sql`
- **Do:** Every table, enum, CHECK, FK action and index from `design.md`'s schema section, verbatim — including `citext`, the two partial unique indexes on `ratings`, the deferrable `exercises` order constraint, and the `lower(name)` uniqueness on both reference lists.
- **Test:** `SchemaIT` queries `information_schema` and `pg_constraint` and asserts every table, every named constraint (`exactly_one_event`, `scores_recorded_together`, `results_sum_to_played`) and every FK's `ON DELETE` action.
- **Done when:** Green on a fresh container. `AC DATA-01…06`
- **Depends on:** T3

### T7 — Entities: `User`, `RefreshToken`, `Team`, `Player` ✅ done
- **Files:** `auth/User.kt`, `auth/RefreshToken.kt`, `team/Team.kt`, `team/Player.kt`, `common/Ids.kt`, repositories
- **Do:** JPA mappings against T6's schema. UUIDv7 via `Ids.kt` (AD-105). `@Version` on mutable entities. `players` mapped as an ordered collection on `Team`.
- **Test:** `@DataJpaTest` round-trip per entity; `ddl-auto: validate` passing is itself the mapping-matches-schema proof.
- **Done when:** Green.
- **Depends on:** T6

### T8 — Entities: `Training`, `Exercise`, `Game`, `RivalRow` ✅ done
- **Files:** `training/Training.kt`, `training/Exercise.kt`, `game/Game.kt`, `standings/RivalRow.kt`, repositories
- **Do:** `Exercise.diagram` mapped as `jsonb` (Hibernate 7 `@JdbcTypeCode(SqlTypes.JSON)`). `order_index` maps to `@OrderColumn`. `duration_minutes` ↔ `duration` naming handled in DTOs, not entities.
- **Test:** `@DataJpaTest` round-trip including a non-trivial `diagram` JSON and a null `diagram`.
- **Done when:** Green.
- **Depends on:** T6

### T9 — Entities: `Card`, `Rating`, `Competition`, `Opponent` ✅ done
- **Files:** `discipline/Card.kt`, `discipline/Rating.kt`, `reference/Competition.kt`, `reference/Opponent.kt`, repositories
- **Do:** `Rating` carries nullable `trainingId`/`gameId` (AD-106) — **no polymorphic column**. Postgres enums mapped explicitly for `card_type`.
- **Test:** `@DataJpaTest` round-trip; a rating with a training and one with a game.
- **Done when:** Green.
- **Depends on:** T6

### T10 — Constraint & cascade test suite ✅ done
- **Files:** `src/test/kotlin/.../ConstraintsIT.kt`, `CascadeIT.kt`
- **Do:** No production code — this task is the safety net for `design.md`'s cascade matrix.
- **Test:** Attempt each illegal write **directly against the repository**, bypassing service validation: a rating with both event FKs set and with neither; a game with one score null; a rival row where `won+drawn+lost ≠ played`; two competitions differing only in case; two ratings for one `(player, game)`. Then walk the full cascade matrix: delete team → players/cards/ratings gone, trainings/games survive with null `team_id`; delete game → cards/ratings gone; delete training → exercises/ratings gone.
- **Done when:** Every constraint rejects, every cascade matches the matrix. `AC OWN-02`
- **Depends on:** T7, T8, T9

---

## Phase 2 — Error contract (3 tasks) · `ERR-01…06`

### T11 — Problem+JSON handler ✅ done
- **Files:** `common/errors.kt`, `common/ApiExceptionHandler.kt`
- **Do:** `NotFoundException`, `ValidationException`, `ConflictException`. One `@RestControllerAdvice` producing RFC 9457 `ProblemDetail` with a stable `type` URI per error kind (AD-109).
- **Test:** A test controller throwing each type returns the right status and `type`.
- **Done when:** Green. `AC ERR-01`
- **Depends on:** T1

### T12 — Bean-validation → field-keyed `errors` ✅ done
- **Files:** `common/ApiExceptionHandler.kt`
- **Do:** `MethodArgumentNotValidException` → `400` with an `errors` object keyed by field name.
- **Test:** A DTO failing two constraints yields both field keys with readable messages.
- **Done when:** Green. `AC ERR-02`
- **Depends on:** T11

### T13 — Infrastructure failure mapping ✅ done
- **Files:** `common/ApiExceptionHandler.kt`
- **Do:** Unhandled → `500`, generic detail, stack trace logged with the correlation id and **nothing leaked**. `DataAccessResourceFailureException` → `503 database-unavailable`. `OptimisticLockingFailureException` → `409 stale-version`. Unparseable UUID path variable → `400`.
- **Test:** Each mapping asserted; the `500` test asserts the response body contains neither the exception class name nor any SQL fragment.
- **Done when:** Green. `AC ERR-03…06`
- **Depends on:** T11

---

## Phase 3 — Auth & ownership (8 tasks) · `AUTH-01…07`, `OWN-01`

### T14 — Registration ✅ done
- **Files:** `auth/AuthService.kt`, `auth/AuthController.kt`, `auth/dto/*`, `config/SecurityConfig.kt` (password encoder)
- **Do:** `POST /auth/register`. bcrypt cost 12. Email uniqueness from the `citext` index, mapped to `409 email-already-registered`.
- **Test:** Success returns `201` + user + tokens; duplicate email (including differing case) → `409`; blank name, malformed email, 7-char password → `400` with field keys and **no user row created**.
- **Done when:** Green. `AC AUTH-01…03`
- **Depends on:** T7, T12

### T15 — JWT issue & verify ✅ done
- **Files:** `auth/JwtService.kt`, `config/SecurityConfig.kt`
- **Do:** Signed access tokens, 15-minute expiry, user id as subject. Secret from env with **no default** in any non-dev profile.
- **Test:** Round-trip; expired token rejected; token signed with a different key rejected as `401`, never `500`. `Edge case: wrong-key JWT`
- **Done when:** Green.
- **Depends on:** T14

### T16 — Login ✅ done
- **Files:** `auth/AuthService.kt`, `auth/AuthController.kt`
- **Do:** `POST /auth/login`.
- **Test:** Correct pair → `200` + token pair. **Wrong password and unknown email return byte-identical bodies** — asserted by comparing the two responses, not by eyeballing them. Password verification runs even for an unknown email so timing does not distinguish the cases.
- **Done when:** Green. `AC AUTH-04, AUTH-05`
- **Depends on:** T15

### T17 — Refresh rotation & reuse detection ✅ done
- **Files:** `auth/RefreshTokenService.kt`, `auth/AuthController.kt`
- **Do:** `POST /auth/refresh` issues a new pair and revokes the presented token. Replaying an already-rotated token revokes **every** token for that user.
- **Test:** Rotation works; the old token then fails; replay of a rotated token invalidates a third, still-valid token belonging to the same user. Only the SHA-256 hash is ever stored — asserted by querying the column.
- **Done when:** Green. `AC AUTH-06, AUTH-07`
- **Depends on:** T16

### T18 — Security filter chain ✅ done
- **Files:** `config/SecurityConfig.kt`
- **Do:** Stateless resource-server chain. Public: `/auth/register`, `/auth/login`, `/auth/refresh`, `/actuator/health`. Everything under `/api/v1/**` authenticated. CORS for the Vite dev origin.
- **Test:** A parameterised test hits **every** protected path with no token and asserts `401`; each public path succeeds without one. An expired token yields `token-expired`, distinguishable from a malformed one.
- **Done when:** Green. `AC AUTH-09, AUTH-10`
- **Depends on:** T15

### T19 — Logout + profile read/update ✅ done
- **Files:** `auth/UserController.kt`, `auth/AuthService.kt`
- **Do:** `POST /auth/logout`, `GET /users/me`, `PATCH /users/me`.
- **Test:** Logout revokes refresh tokens → `204`. `GET` never includes `passwordHash` — asserted against the **raw JSON string**, not a deserialized DTO, since a DTO cannot expose a field it does not declare. `PATCH` to another user's email → `409` with nothing changed.
- **Done when:** Green. `AC AUTH-P3.1…3.3`
- **Depends on:** T17

### T20 — Password change ✅ done
- **Files:** `auth/UserController.kt`, `auth/AuthService.kt`
- **Do:** `PUT /users/me/password` — verify current, rehash, revoke all refresh tokens.
- **Test:** Success → `204`, old refresh token now rejected, login with the new password succeeds. Wrong current password → `400 incorrect-password` and the stored hash is **unchanged** (asserted by re-authenticating with the old password).
- **Done when:** Green. `AC AUTH-P3.4, P3.5`
- **Depends on:** T19

### T21 — Ownership plumbing + isolation harness ✅ done
- **Files:** `common/OwnedRepository.kt`, `common/CurrentUser.kt`, `src/test/kotlin/.../OwnershipIsolationIT.kt`
- **Do:** Resolve `ownerId` from the JWT subject into controller methods. `OwnedRepository` shapes every finder to take `ownerId`, so an unscoped query does not compile. **No endpoint accepts an owner id from the request.**
- **Test:** A reusable two-user fixture plus a parameterised test that later phases extend one line at a time as each entity lands.
- **Done when:** Green with the entities that exist so far. `AC OWN-01`
- **Depends on:** T18

---

## Phase 4 — Teams & players (5 tasks) · `TEAM-01…04`, `PLAY-01…04`

### T22 — Team CRUD ✅ done
- **Files:** `team/TeamService.kt`, `team/TeamController.kt`, `team/dto/*`
- **Do:** `GET /teams`, `GET /teams/{id}`, `POST /teams`, `PATCH /teams/{id}`. Players nested, ordered by shirt number then name.
- **Test:** List returns only the caller's teams; create returns `201` + `Location` + empty `players[]`; `PATCH` with a partial body touches only those fields and **never** `players`; an empty `PATCH` body returns `200` unchanged. `Edge case: empty PATCH`
- **Done when:** Green. `AC TEAM-01…04`
- **Depends on:** T21

### T23 — Team delete + cascade ✅ done
- **Files:** `team/TeamService.kt`, `team/TeamController.kt`
- **Do:** `DELETE /teams/{id}` relying on the schema's FK actions (AD-107) — **no manual cascade code**.
- **Test:** After deletion: players, their cards and their ratings are gone; that team's trainings and games still exist with `teamId: null`. Fixtures are created through the API where those endpoints exist, and via repositories where they do not yet.
- **Done when:** Green. `AC TEAM-05`
- **Depends on:** T22

### T24 — Player create & update ✅ done
- **Files:** `team/PlayerController.kt`, `team/PlayerService.kt`, `team/dto/*`
- **Do:** `POST` / `PATCH` under `/teams/{teamId}/players`. Position validated against the 17-code enum — deliberately stricter than the frontend's free-text field. `PATCH` accepts the stats the frontend's popup cannot currently edit.
- **Test:** Valid create → `201`; `shirtNumber` 0 and 100 → `400`; `age` 3 and 100 → `400`; `position: "STRIKER"` → `400` with nothing persisted; a stats-only `PATCH` updates goals/assists/concededGoals.
- **Done when:** Green. `AC PLAY-06…09`
- **Depends on:** T22

### T25 — Player read & delete
- **Files:** `team/PlayerController.kt`
- **Do:** `GET` list, `GET` one, `DELETE`.
- **Test:** Delete removes the player's cards and ratings → `204`. A `playerId` that exists but under a different `teamId` in the path → `404`.
- **Done when:** Green. `AC PLAY-10, PLAY-11`
- **Depends on:** T24

### T26 — Team/player isolation
- **Files:** `OwnershipIsolationIT.kt`
- **Do:** Extend the T21 harness to teams and players.
- **Test:** User B receives `404` — never `403` — reading, patching and deleting user A's team, and on every player path beneath it.
- **Done when:** Green. `AC TEAM-02`
- **Depends on:** T25

---

## Phase 5 — Trainings & exercises (5 tasks) · `TRAIN-01…06`, `EXER-01`

### T27 — Training numbering (pure logic)
- **Files:** `training/TrainingNumbering.kt` + unit test
- **Do:** Port the frontend's `numberTrainings` (AD-108): group by team, order by `day` then id, 1-based; `teamId: null` → `number: null`.
- **Test:** Out-of-order input numbers correctly; **equal `day` values break the tie by id, deterministically across repeated runs**; unassigned trainings get null; empty input returns empty. `Edge case: identical days`
- **Done when:** Green.
- **Depends on:** T8

### T28 — Training create & update
- **Files:** `training/TrainingService.kt`, `training/TrainingController.kt`, `training/dto/*`
- **Do:** `POST /trainings`, `PATCH /trainings/{id}`. Exercises replaced wholesale when the key is present, untouched when absent — one transaction. `duration` ↔ `duration_minutes` mapping.
- **Test:** Create with three exercises persists all three in order; `PATCH` with two replaces all three; `PATCH` **without** the `exercises` key leaves them intact (the distinction between absent and empty-array); `duration: 0` and `duration: 481` → `400`; a `teamId` owned by another user → `400 unknown-team`, not `404`.
- **Done when:** Green. `AC TRAIN-05…08, TRAIN-12`
- **Depends on:** T21, T27

### T29 — Training reads & filters
- **Files:** `training/TrainingController.kt`, `training/TrainingService.kt`
- **Do:** `GET /trainings`, `?teamId=`, `?assigned=false`, `GET /trainings/{id}` — all carrying computed `number`.
- **Test:** Three trainings created out of date order return numbers 1/2/3 in date order. **`?teamId=` returns the same `number` values as the unfiltered call** — the regression that appears if numbering runs after filtering instead of before. `?assigned=false` returns only null-team trainings.
- **Done when:** Green. `AC TRAIN-01…04`
- **Depends on:** T28

### T30 — Diagram validation
- **Files:** `training/DiagramValidator.kt`
- **Do:** Enforce the frontend's `LIMITS` and `SHAPE_KINDS`: ≤8192 bytes, ≤60 shapes, kind in the 8-item list. Coordinates outside 0–1 are **clamped**, mirroring `clampToPitch` — not rejected.
- **Test:** 61 shapes → `400`; 8193 bytes → `400`; `kind: "spaceship"` → `400`; `x: 1.7` is stored as `1.0`; `diagram: null` round-trips as null, never `{}`. `AC TRAIN-09, TRAIN-10`
- **Done when:** Green.
- **Depends on:** T28

### T31 — Training delete + cascade
- **Files:** `training/TrainingService.kt`
- **Do:** `DELETE /trainings/{id}`; exercises and that training's ratings go via FK cascade.
- **Test:** Exercises and ratings gone; ratings for *other* trainings untouched. Extend the isolation harness to trainings.
- **Done when:** Green. `AC TRAIN-11`
- **Depends on:** T29

---

## Phase 6 — Games & standings (6 tasks) · `GAME-01…07`, `STAND-01…04`

### T32 — Game create & update
- **Files:** `game/GameService.kt`, `game/GameController.kt`, `game/dto/*`
- **Do:** `POST /games`, `GET /games/{id}`, `PATCH /games/{id}`. Create forces both scores null and **ignores** any supplied. `PATCH` **rejects** score fields with `400`.
- **Test:** Create with `usScore: 3` in the body → created with null scores; `PATCH` with `usScore` → `400`.
- **Done when:** Green. `AC GAME-03, GAME-04`
- **Depends on:** T21

### T33 — Game reads & filters
- **Files:** `game/GameController.kt`
- **Do:** `GET /games` (date descending), `?teamId=`, `?status=scheduled|played`, `?assigned=false`.
- **Test:** **A `0-0` game is classified `played`** — the null-vs-zero trap, and the single most important assertion in this phase. `?assigned=false` returns only null-team games.
- **Done when:** Green. `AC GAME-01, GAME-02`
- **Depends on:** T32

### T34 — Result record & clear
- **Files:** `game/GameController.kt`, `game/GameService.kt`
- **Do:** `PUT /games/{id}/result`, `DELETE /games/{id}/result`.
- **Test:** Record `0-0` → both stored as 0, game reads as played. Clear → both null, **fixture still exists**. A score of `-1` and of `100` → `400`.
- **Done when:** Green. `AC GAME-05, GAME-06`
- **Depends on:** T32

### T35 — Game delete + cascade
- **Files:** `game/GameService.kt`
- **Test:** That game's cards and ratings are gone; another game's are untouched. Extend the isolation harness to games.
- **Done when:** Green. `AC GAME-07`
- **Depends on:** T33

### T36 — Rival standings rows
- **Files:** `standings/RivalRowService.kt`, `standings/StandingsController.kt`, dto
- **Do:** CRUD on `/standings/rivals`. `points` and `goalDifference` in a request body are **ignored**, never stored.
- **Test:** `won+drawn+lost ≠ played` → `400` naming the mismatch; a negative figure → `400`; an all-zero row with `played: 0` is **accepted**; a body carrying `points: 99` is accepted with that value ignored. `Edge case: zero-played rival row`
- **Done when:** Green. `AC STAND-08…10`
- **Depends on:** T21

### T37 — Standings calculation
- **Files:** `standings/StandingsCalculator.kt` + unit test, `StandingsController.kt`
- **Do:** Port `computeOurRow` + `toStandingsRow` + `sortStandings` (AD-108). `GET /standings?teamId=`.
- **Test:** Only played games count — a scheduled fixture contributes nothing. `points = won*3 + drawn`. The full four-level sort (points → GD → goals for → name) exercised by a table where each level is the actual tiebreak. A team with no played games yields an **all-zero row, present, never absent and never null**.
- **Done when:** Green. `AC STAND-11, STAND-12`
- **Depends on:** T34, T36

---

## Phase 7 — Cards & ratings (5 tasks) · `CARD-01…03`, `RATE-01…04`

### T38 — Card endpoints
- **Files:** `discipline/CardService.kt`, `CardController.kt`, dto
- **Do:** `GET /cards?gameId=&playerId=`, `POST /cards`, `DELETE /cards/{id}`.
- **Test:** A player not in the team playing that game → `400 player-not-in-game-team`; `type: "orange"` → `400`; both filters work alone and together.
- **Done when:** Green. `AC CARD-01…05`
- **Depends on:** T25, T35

### T39 — Rating reads
- **Files:** `discipline/RatingService.kt`, `RatingController.kt`, `RatingMapper.kt`, dto
- **Do:** `GET /ratings?eventType=&eventId=&playerId=`. The mapper derives `eventType`/`eventId` from the FK columns (AD-106).
- **Test:** A training-rating serializes as `eventType: "training"` with `eventId` equal to the training id, and the game case likewise. Filtering by each parameter works.
- **Done when:** Green. `AC RATE-06`
- **Depends on:** T31, T35

### T40 — Rating upsert
- **Files:** `discipline/RatingService.kt`, `RatingController.kt`
- **Do:** `PUT /ratings/{eventType}/{eventId}/players/{playerId}`. Insert on first call, update after — catching the partial-unique-index violation and retrying as an update, so concurrency is handled by the database rather than a check-then-act race.
- **Test:** Two sequential calls leave exactly one row with the second value. **Concurrent calls for the same triple leave exactly one row** — asserted with parallel requests, not asserted by inspection.
- **Done when:** Green. `AC RATE-07`
- **Depends on:** T39

### T41 — Rating null/zero semantics + delete
- **Files:** `discipline/RatingService.kt`
- **Do:** `value: null` deletes and returns `204` — it never stores a null row. `DELETE /ratings/{id}`.
- **Test:** The three-state walk from the spec's independent test: set 5 → set 0 → set null. **`0` is a stored, readable rating; `null` leaves no row.** A non-integer, `-1` and `11` → `400`. An unknown `eventType`, or an event belonging to another user → `400`.
- **Done when:** Green. `AC RATE-08…11`
- **Depends on:** T40

### T42 — Discipline cascade & isolation
- **Files:** `CascadeIT.kt`, `OwnershipIsolationIT.kt`
- **Test:** Deleting a player, a game and a training each remove exactly the right cards/ratings and nothing else. Extend the isolation harness to cards and ratings — completing the walk over every entity type. `Edge case: rating written for a concurrently deleted game → 400, never an orphan`
- **Done when:** Green.
- **Depends on:** T41

---

## Phase 8 — Reference lists (4 tasks) · `COMP-01…04`, `OPP-01…04`

### T43 — Generic reference-list service + competitions
- **Files:** `reference/ReferenceListService.kt`, `CompetitionController.kt`, dto
- **Do:** One service parameterised by entity and target `games` column — **not two near-identical services** (AD-104; the frontend's AD-016 records what the duplicated version cost). `GET`/`POST`/`DELETE /competitions`. Names stored trimmed; list ordered case-insensitively.
- **Test:** `"  Cup  "` is stored as `"Cup"`; a whitespace-only name → `400`; ordering is case-insensitive; `DELETE` removes the entry and **leaves the name on historical games**.
- **Done when:** Green. `AC COMP-01, COMP-02, COMP-04, COMP-09`
- **Depends on:** T32

### T44 — Opponents via the same service
- **Files:** `reference/OpponentController.kt`
- **Do:** Wire opponents through `ReferenceListService`. No logic duplicated from T43.
- **Test:** The T43 suite re-run against `/opponents`, parameterised over both endpoints rather than copied.
- **Done when:** Green. `AC OPP-01…04`
- **Depends on:** T43

### T45 — Rename cascade
- **Files:** `reference/ReferenceListService.kt`
- **Do:** `PATCH` renames the entry and rewrites the matching `games` column for the caller's games, matching case-insensitively on the trimmed old name — **one transaction**.
- **Test:** Rename with two matching games → both updated. Renaming an opponent **does not touch a rival standings row sharing that name** (frontend AD-010). A no-op rename to the same trimmed name performs no cascade and returns `200`. **A forced mid-cascade failure rolls back entirely** — no game is left carrying a name whose entry no longer exists. Nothing but a transactional implementation passes this.
- **Done when:** Green. `AC COMP-05…08, OPP-05…08`
- **Depends on:** T44

### T46 — Case-insensitive uniqueness
- **Files:** `reference/ReferenceListService.kt`
- **Do:** Map the `uq_*_owner_name` violation to `409`. The **index** is the enforcement; the service only translates the error (the frontend's `normalize()+some()` check has a TOCTOU race that an index does not).
- **Test:** `"Cup"` then `"cup"` → `409`. `"Cup"` then `"  CUP  "` → `409`. The same name under two different users → both succeed.
- **Done when:** Green. `AC COMP-03, OPP-03`
- **Depends on:** T45

---

## Phase 9 — Docs & packaging (3 tasks) · P2/P3 · `DOC-01`, `EXER-02`, `DEPLOY-01`

### T47 — OpenAPI + Swagger UI
- **Files:** `build.gradle.kts`, `config/OpenApiConfig.kt`
- **Do:** springdoc — **verify the version is Boot-4 compatible before adding it** (AD-101). Document the bearer scheme, every endpoint and the problem+json error shape.
- **Test:** `/v3/api-docs` returns OpenAPI 3.1 containing every path from `spec.md`'s inventory; the count is asserted, not eyeballed.
- **Done when:** Green. `AC DOC-01`
- **Depends on:** T46

### T48 — Exercise sub-resources (P3)
- **Files:** `training/ExerciseController.kt`
- **Do:** `POST`/`PATCH`/`DELETE` under `/trainings/{id}/exercises`. Deleting re-packs `order_index` contiguously.
- **Test:** Deleting the middle of three leaves indexes 0 and 1, with no gap.
- **Done when:** Green. `AC EXER-02`
- **Depends on:** T30

### T49 — API image (P3)
- **Files:** `Dockerfile`, `docker-compose.yml`, `.dockerignore`
- **Do:** Multi-stage build, non-root runtime user, JRE 21 base. `api` service behind the `full` compose profile, waiting on the db healthcheck.
- **Test:** `docker compose --profile full up` → `/actuator/health` returns `200` from the container. Manual, recorded in the commit message.
- **Done when:** Verified. `AC DEPLOY-01`
- **Depends on:** T47

---

## Execution order & batching

49 tasks — well past the ~8-task inline threshold, so **sub-agent delegation
will be offered before Execute begins** (offer-then-confirm; nothing is
dispatched without approval). Batches are whole phases, run sequentially; a
batch never starts before the previous one reports every task complete.

| Batch | Phases | Tasks | Delivers |
| --- | --- | --- | --- |
| 1 | 0 | T1–T5 | Boots, Dockerised DB, migrations, health. **Gate on T1's spike result.** |
| 2 | 1–2 | T6–T13 | Whole schema, entities, cascades proven, error contract |
| 3 | 3 | T14–T21 | Auth end-to-end + ownership plumbing |
| 4 | 4 | T22–T26 | Teams & players |
| 5 | 5 | T27–T31 | Trainings, exercises, diagrams |
| 6 | 6 | T32–T37 | Games, results, standings |
| 7 | 7 | T38–T42 | Cards & ratings |
| 8 | 8–9 | T43–T49 | Reference lists, rename cascade, docs, packaging |

**Critical path:** T1 → T3 → T6 → T7 → T14 → T18 → T21 → everything else.
Phases 4–8 depend on Phase 3 but are independent of each other, so they can be
reordered freely if priorities shift.

**After T49 the Verifier runs automatically** — a fresh sub-agent, author ≠
verifier, doing a spec-anchored outcome check plus a discrimination sensor
(injected faults that the suite must kill), writing `validation.md`. Not
optional, not prompted.

### Highest-risk tasks

| Task | Why | Signal it went wrong |
| --- | --- | --- |
| T1 | AD-110's flagged Boot 4.1 × Testcontainers v2 risk | Context fails to start; **stop and report, don't work around** |
| T4 | Jackson 3 package/coordinate churn | `usScore: null` silently dropped from responses |
| T29 | Numbering after filtering instead of before | `?teamId=` returns 1,2,3 where the unfiltered call returns 4,5,6 |
| T40 | Check-then-act instead of index-then-retry | Two rating rows for one triple under concurrency |
| T45 | Non-transactional cascade | Games carry a name whose list entry is gone |
| T33/T34/T41 | The null-vs-zero trap, in three places | A `0-0` game reads as unplayed; a `0` rating reads as unrated |
