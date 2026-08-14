# Coach Planner API — Backend MVP Specification

**Scope:** Complex (new domain, new repository, multi-component)
**Created:** 2026-08-14
**Status:** Specified — awaiting execution

## Problem Statement

The Coach Planner React app keeps every team, player, training, game, card and
rating in `localStorage` behind a mock `AuthContext`. That means one browser on
one device, a hard ~5MB ceiling (the frontend's AD-002), no sharing, no backup,
and credentials stored as plaintext the frontend's own decision log calls
"consistent, not secure" (AD-011). A coach who clears their browser loses a
season. This feature replaces that store with a Kotlin HTTP API over PostgreSQL,
with real authentication and per-user data ownership.

## Goals

- [ ] Every one of the frontend's 8 service modules has a complete HTTP
      equivalent — 100% of the ~50 service methods reachable as endpoints, so
      the swap needs no new frontend capability
- [ ] Registration and login issue JWTs; every endpoint returns only the
      authenticated coach's data, verified by a test that asserts one user
      cannot read another's team (404, not 403)
- [ ] `docker compose up -d db` yields a working PostgreSQL 18 with the full
      schema applied by Flyway on first boot
- [ ] Integration tests run against real PostgreSQL and cover every endpoint's
      success path, validation failures, ownership isolation, and cascade
      behaviour

## Out of Scope

| Feature | Reason |
| --- | --- |
| Rewiring the React app to call this API | User selection: backend + Docker only this round. Needs its own feature in the frontend repo, including token storage and the `Date` re-hydration the frontend's `docs/05-services.md` already flags. |
| Importing existing `localStorage` data | User selection. No production data exists yet; an import path is cheap to add later and expensive to design blind. |
| Deploying the API (hosting, CI/CD, managed Postgres) | Docker Compose covers local development, which is what was asked for. A `Dockerfile` for the API itself is P3 below. |
| Password reset / email verification / OAuth | Registration and login only. Email delivery is an external dependency this MVP does not take on. |
| Multi-coach collaboration (sharing a team, roles, invites) | Ownership is strictly single-owner. Sharing is a data-model feature, not a permission tweak. |
| File/image upload for `player.avatar` or `exercise.image` | The frontend has no upload UI; `exercise.image` is a legacy field its AD-015 explicitly left unused. Diagrams are JSON, not files. |
| Real-time updates (WebSocket/SSE) | The frontend re-reads after every mutation. Polling is sufficient and adds no infrastructure. |
| Per-competition standings tables | The frontend's AD-010 keeps competitions as name strings, which cannot support this without reversing that decision (AD-104). |
| Rate limiting, API keys, quotas | Single-user personal tool. Recorded as an accepted gap, not an oversight — see the dimensions sweep below. |

---

## Assumptions & Open Questions

Every ambiguity is resolved or recorded here.

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Auth model | Real auth + per-user ownership | User selected | **y** |
| Stack | Kotlin 2.4 / Spring Boot 4.1 / JPA / Flyway | User selected | **y** |
| Database | PostgreSQL 18 in Docker | User delegated the choice; see AD-102 | **y** |
| Competitions & opponents | Name strings + explicit rename cascade (frontend AD-010 preserved) | User selected | **y** |
| Round scope | Backend + Docker only | User selected | **y** |
| Id type | UUIDv7 strings | Matches frontend AD-003's `crypto.randomUUID()` exactly, so no id translation layer | n — agent default (AD-105) |
| Rival standings rows have no team | One shared league table per **user**, not per team | The frontend's `standings` records carry no `teamId` and `standingsService` has no team parameter. Adding one would change frontend behaviour, which is out of scope. **This is a real modelling gap**: a coach with two teams in different divisions gets one mixed table. Flagged for a future feature rather than silently fixed. | n — agent default |
| Exercises on write | Replaced wholesale as a nested array on training create/update | `TrainingSavePopup` already submits the whole training with its `exercises[]`. Granular sub-resource endpoints are specified as P2 for future use, not required for parity. | n — agent default |
| `training.day` / `game.date` wire format | ISO-8601 UTC with offset (`2024-10-24T15:00:00Z`), stored `timestamptz` | The frontend's seed uses `Z`-suffixed instants and its store already re-hydrates `Date` fields on read (`DATE_FIELDS` in `store.js`). | n — agent default |
| Access token lifetime | 15 min access / 30 day rotating refresh | Standard. Short access token limits blast radius; long refresh avoids daily logins for a personal tool. | n — agent default |
| Password hashing | bcrypt, cost 12 | Spring Security's default `DelegatingPasswordEncoder` idiom; cost 12 is the current sensible floor. | n — agent default |
| Reading another user's record | `404 Not Found`, never `403 Forbidden` | A `403` confirms the id exists, leaking that another coach has that team. Ownership filtering happens in the query, so a miss is indistinguishable from a nonexistent row. | n — agent default |
| Empty-list responses | `200` with `[]`, never `404` | A coach with no teams is a valid state, not an error. | n — agent default |
| API versioning | Path prefix `/api/v1` | Cheapest reversible choice; header-based negotiation buys nothing at v1. | n — agent default |
| Rate limiting | None | Single-user tool behind auth; no untrusted callers. Deliberate gap, revisit if ever multi-tenant in earnest. | n — agent default |
| Observability | Actuator health/info/metrics + structured request logging. No tracing, no APM. | Proportionate to a single-instance personal backend. | n — agent default |
| Idempotency keys | None. `PUT` endpoints are naturally idempotent; `POST` creates are not. | A duplicate `POST /games` creates two games — recoverable by deleting one, and the frontend submits from a modal that closes on success. Not worth a dedup table. | n — agent default |
| Concurrency control | `@Version` optimistic locking on mutable entities, `409 Conflict` on stale write | One coach on two devices is a plausible real conflict. Pessimistic locking is overkill. | n — agent default |
| Soft deletes | None — deletes are permanent | Mirrors current frontend behaviour. Undo was never a product requirement. | n — agent default |

**Open questions:** none — all resolved or logged above.

### Implicit-requirement dimensions sweep (Complex ⇒ full sweep)

| Dimension | Resolution |
| --- | --- |
| Input validation & bounds | `ERR-02`, plus per-entity ACs (rating 0–10, card type enum, standings sum rule, non-empty reference names) |
| Failure / partial-failure states | `COMP-03`/`OPP-03` transactional rename cascade; `ERR-04` DB-unavailable handling |
| Idempotency / retry / duplicate handling | Logged as an assumption above — `PUT` idempotent, `POST` not, no dedup keys |
| Auth boundaries & rate limits | `AUTH-01`…`AUTH-07`, `OWN-01`; rate limits explicitly N/A (single-user tool, logged above) |
| Concurrency / ordering | `ERR-05` optimistic locking; `TRAIN-02` deterministic training numbering under equal dates |
| Data lifecycle / expiry | `OWN-02` cascade rules (AD-107); refresh-token expiry and rotation `AUTH-04`; no soft deletes (logged) |
| Observability | `INFRA-05` — actuator + structured logging; tracing N/A for a single instance |
| External-dependency failure | The database is the only external dependency — `ERR-04`. No third-party APIs are called. |
| State-transition integrity | `GAME-05`/`GAME-06` result record/clear as explicit transitions; `RATE-02` upsert-vs-clear null-vs-zero rule |

---

## User Stories

### P1: Project skeleton, Docker database and schema ⭐ MVP

**User Story**: As the developer, I want a Kotlin service that boots against a
Dockerised PostgreSQL with the full schema applied, so that every later story
has somewhere to store data.

**Why P1**: Nothing else can be built or tested without it. AD-110 flags the
Boot 4.1 + Testcontainers v2 combination as the plan's biggest unverified
assumption — it must be proven first, not discovered at Phase 5.

**Acceptance Criteria**:

1. WHEN `docker compose up -d db` runs THEN a PostgreSQL 18 container SHALL start with a named volume, a healthcheck, and credentials read from `.env`
2. WHEN the application starts against an empty database THEN Flyway SHALL apply `V1__init.sql` and create every table in the design's schema
3. WHEN the application starts against an already-migrated database THEN Flyway SHALL apply nothing and the service SHALL start cleanly
4. WHEN `GET /actuator/health` is called THEN the system SHALL return `200` with `status: "UP"` and a `db` component that reflects real connectivity
5. WHEN the database is unreachable at startup THEN the service SHALL fail fast with a log line naming the JDBC URL, and SHALL NOT start in a degraded state
6. WHEN the integration test suite runs THEN it SHALL provision PostgreSQL via Testcontainers `@ServiceConnection` and SHALL NOT use H2 or any in-memory substitute

**Independent Test**: `docker compose up -d db && ./gradlew test` — green suite plus a `200` from the health endpoint.

---

### P2: Register, log in, and stay logged in ⭐ MVP

**User Story**: As a coach, I want to create an account and log in, so that my
data is mine and follows me across devices.

**Why P1-critical**: AD-103 — ownership cannot be retrofitted. Every other
endpoint is scoped by the caller resolved here.

**Acceptance Criteria**:

1. WHEN `POST /api/v1/auth/register` receives a unique email, a non-empty name and a password of ≥8 characters THEN the system SHALL create the user, hash the password with bcrypt cost 12, and return `201` with the user and a token pair
2. WHEN registration receives an email already in use THEN the system SHALL return `409` with problem type `email-already-registered`, and SHALL NOT reveal whether that address belongs to a real active account beyond that fact
3. WHEN registration receives a malformed email, a blank name, or a password under 8 characters THEN the system SHALL return `400` with a field-keyed `errors` member and SHALL NOT create a user
4. WHEN `POST /api/v1/auth/login` receives a correct email/password pair THEN the system SHALL return `200` with a 15-minute access token and a 30-day refresh token
5. WHEN login receives a wrong password OR an unregistered email THEN the system SHALL return `401` with the identical body and an indistinguishable response time in both cases
6. WHEN `POST /api/v1/auth/refresh` receives a valid, unexpired, unrevoked refresh token THEN the system SHALL return a new token pair AND revoke the presented refresh token (rotation)
7. WHEN a refresh token that has already been rotated is presented again THEN the system SHALL return `401` AND revoke every refresh token for that user (reuse detection)
8. WHEN `POST /api/v1/auth/logout` is called with a valid access token THEN the system SHALL revoke the caller's refresh tokens and return `204`
9. WHEN any `/api/v1/**` endpoint other than `/auth/register`, `/auth/login`, `/auth/refresh` and `/actuator/health` is called without a valid access token THEN the system SHALL return `401`
10. WHEN an expired access token is presented THEN the system SHALL return `401` with problem type `token-expired`, distinguishable from a malformed token, so a client knows to refresh rather than re-login

**Independent Test**: Register → call `/teams` with the token → `200`. Call `/teams` with no token → `401`. Refresh → old refresh token rejected.

---

### P3: Manage my profile ⭐ MVP

**User Story**: As a coach, I want to see and change my name, email and
password, so the Settings → Profile tab keeps working.

**Why P1-critical**: The frontend's `24-profile-settings` already ships this UI
against `updateProfile`/`changePassword`; without it, that screen has no backend.

**Acceptance Criteria**:

1. WHEN `GET /api/v1/users/me` is called with a valid token THEN the system SHALL return the caller's id, name, email and creation timestamp, and SHALL NOT include the password hash
2. WHEN `PATCH /api/v1/users/me` receives a valid name and/or email THEN the system SHALL update only the supplied fields and return the updated user
3. WHEN `PATCH /api/v1/users/me` receives an email already registered to another user THEN the system SHALL return `409` and change nothing
4. WHEN `PUT /api/v1/users/me/password` receives a correct current password and a new password of ≥8 characters THEN the system SHALL update the hash, revoke every existing refresh token, and return `204`
5. WHEN the password change receives an incorrect current password THEN the system SHALL return `400` with problem type `incorrect-password` and SHALL NOT change the stored hash

**Independent Test**: Change password → old refresh token rejected → login with the new password succeeds.

---

### P4: Teams and players ⭐ MVP

**User Story**: As a coach, I want to create teams and manage their squads, so
`teamService` has a backend.

**Acceptance Criteria**:

1. WHEN `GET /api/v1/teams` is called THEN the system SHALL return only the caller's teams, each with its `players[]` nested, ordered by shirt number then name
2. WHEN `GET /api/v1/teams/{id}` names a team owned by another user OR a nonexistent id THEN the system SHALL return `404` in both cases with an identical body
3. WHEN `POST /api/v1/teams` receives a non-empty `name` THEN the system SHALL create the team owned by the caller with an empty `players[]` and return `201` with a `Location` header
4. WHEN `PATCH /api/v1/teams/{id}` receives any subset of `name`, `club`, `season` THEN the system SHALL update only those fields and SHALL NOT touch `players`
5. WHEN `DELETE /api/v1/teams/{id}` succeeds THEN the system SHALL delete its players and, through them, their cards and ratings, and SHALL set `team_id` to null on that team's trainings and games rather than deleting them (AD-107)
6. WHEN `POST /api/v1/teams/{teamId}/players` receives a valid player THEN the system SHALL create it against that team and return `201`
7. WHEN a player is created or updated with `shirtNumber` outside 1–99, or `age` outside 4–99 THEN the system SHALL return `400` and persist nothing
8. WHEN a player is created with a `position` outside the 17-code list (`GK`,`RB`,`CB`,`LB`,`RWB`,`LWB`,`CDM`,`RM`,`CM`,`LM`,`CAM`,`RW`,`LW`,`ST`,`CF`,`RF`,`LF`) THEN the system SHALL return `400` — the frontend accepts free text here, and the API is deliberately stricter
9. WHEN `PATCH /api/v1/teams/{teamId}/players/{playerId}` is called THEN the system SHALL update the supplied fields, including the stats `goals`/`assists`/`concededGoals` that the frontend's popup cannot currently edit
10. WHEN `DELETE /api/v1/teams/{teamId}/players/{playerId}` succeeds THEN the system SHALL delete that player's cards and ratings and return `204`
11. WHEN a player endpoint is called with a `playerId` that exists but belongs to a different `teamId` in the path THEN the system SHALL return `404`

**Independent Test**: Create team → add player → delete team → player, cards and ratings are gone; that team's games survive with `teamId: null`.

---

### P5: Trainings and exercises ⭐ MVP

**User Story**: As a coach, I want to plan training sessions with their
exercises and diagrams, so `trainingService` has a backend.

**Acceptance Criteria**:

1. WHEN `GET /api/v1/trainings` is called THEN the system SHALL return the caller's trainings, each carrying its computed `number` and its `exercises[]` in stored order
2. WHEN trainings are numbered THEN each SHALL receive a 1-based position **within its team**, ordered by `day` ascending with ties broken by id, and a training with `teamId: null` SHALL receive `number: null` (mirrors frontend AD-006 and `numberTrainings`)
3. WHEN `GET /api/v1/trainings?teamId={id}` is called THEN the system SHALL return only that team's trainings, and their `number` values SHALL be identical to those in the unfiltered response
4. WHEN `GET /api/v1/trainings?assigned=false` is called THEN the system SHALL return only trainings whose `teamId` is null
5. WHEN `POST /api/v1/trainings` receives a `day`, a `duration` and an `exercises[]` array THEN the system SHALL create the training and all its exercises in one transaction and return `201`
6. WHEN a training is created or updated with `duration` ≤ 0 or > 480 minutes THEN the system SHALL return `400`
7. WHEN `PATCH /api/v1/trainings/{id}` includes an `exercises` array THEN the system SHALL replace the training's exercises wholesale with that array, preserving its order, and SHALL delete any ratings belonging to removed exercises' training only if the training itself is deleted (exercises carry no ratings)
8. WHEN `PATCH /api/v1/trainings/{id}` omits `exercises` THEN the system SHALL leave the existing exercises untouched
9. WHEN an exercise carries a `diagram` THEN the system SHALL store it as `jsonb`, SHALL reject a payload over 8192 bytes or more than 60 shapes with `400`, and SHALL reject any shape whose `kind` is outside the frontend's 8-kind list (matching frontend `LIMITS` and `SHAPE_KINDS`)
10. WHEN an exercise carries `diagram: null` THEN the system SHALL store and return null, not an empty object
11. WHEN `DELETE /api/v1/trainings/{id}` succeeds THEN the system SHALL delete its exercises and every rating whose event is that training, and return `204`
12. WHEN `POST /api/v1/trainings` names a `teamId` owned by another user THEN the system SHALL return `400` with problem type `unknown-team`, not `404`, because the failing resource is the request body's reference rather than the endpoint's own subject

**Independent Test**: Create three trainings for one team on different dates → `GET` returns numbers 1, 2, 3 in date order regardless of creation order.

---

### P6: Games, results and the league table ⭐ MVP

**User Story**: As a coach, I want to schedule fixtures, record results and
maintain a league table, so `gameService` and `standingsService` have a backend.

**Acceptance Criteria**:

1. WHEN `GET /api/v1/games` is called THEN the system SHALL return the caller's games ordered by date descending
2. WHEN `GET /api/v1/games?teamId={id}`, `?status=scheduled`, `?status=played`, or `?assigned=false` is called THEN the system SHALL filter accordingly, and `status` SHALL be decided by whether **both** `usScore` and `themScore` are non-null — a recorded `0-0` SHALL be classified as played (the null-vs-zero trap the frontend's `hasResult` exists to prevent)
3. WHEN `POST /api/v1/games` receives an `opponent`, a `date` and `isHome` THEN the system SHALL create the game with `usScore` and `themScore` explicitly null and return `201`, and SHALL ignore any scores supplied in the create body
4. WHEN `PATCH /api/v1/games/{id}` is called THEN the system SHALL update the supplied fields, and SHALL reject `usScore`/`themScore` in this body with `400` — scores move only through the result endpoints
5. WHEN `PUT /api/v1/games/{id}/result` receives two integers in 0–99 THEN the system SHALL store them and return the updated game
6. WHEN `DELETE /api/v1/games/{id}/result` is called THEN the system SHALL set both scores to null and return the updated game, leaving the fixture itself intact
7. WHEN `DELETE /api/v1/games/{id}` succeeds THEN the system SHALL delete every card and every rating tied to that game, and return `204`
8. WHEN `GET /api/v1/standings/rivals` is called THEN the system SHALL return the caller's manually-maintained rival rows
9. WHEN a rival row is created or updated with `won + drawn + lost` not equal to `played`, or any negative figure THEN the system SHALL return `400` with a message naming the mismatch
10. WHEN a rival row is created or updated with `points` or `goalDifference` in the body THEN the system SHALL ignore those fields — both are always derived, never stored (frontend AD-008)
11. WHEN `GET /api/v1/standings?teamId={id}` is called THEN the system SHALL return the full sorted table: the caller's own row computed from that team's played games plus every rival row, each with derived `points` (3/1/0) and `goalDifference`, ordered by points, then goal difference, then goals for, then name
12. WHEN a team has no played games THEN its own row SHALL appear with all figures zero, never absent and never null

**Independent Test**: Record a `0-0` → the game appears under `?status=played` and contributes a draw and one point to the standings row.

---

### P7: Cards and ratings ⭐ MVP

**User Story**: As a coach, I want to record bookings and rate players per
event, so `cardService` and `ratingService` have a backend.

**Acceptance Criteria**:

1. WHEN `GET /api/v1/cards?gameId=` or `?playerId=` is called THEN the system SHALL return the matching cards for the caller
2. WHEN `POST /api/v1/cards` receives a `playerId`, a `gameId` and a `type` of `yellow` or `red` THEN the system SHALL create the card and return `201`
3. WHEN a card names a player who is not in the team playing that game THEN the system SHALL return `400` with problem type `player-not-in-game-team`
4. WHEN a card names a `type` outside `yellow`/`red` THEN the system SHALL return `400`
5. WHEN `DELETE /api/v1/cards/{id}` is called THEN the system SHALL delete it and return `204`
6. WHEN `GET /api/v1/ratings?eventType=&eventId=` or `?playerId=` is called THEN the system SHALL return matching ratings, each serialized with `eventType` and `eventId` derived from the stored FK columns (AD-106)
7. WHEN `PUT /api/v1/ratings/{eventType}/{eventId}/players/{playerId}` receives an integer `value` in 0–10 THEN the system SHALL upsert keyed on that triple — creating on first call, overwriting on subsequent calls — and SHALL never produce two rows for one triple even under concurrent calls
8. WHEN that endpoint receives `value: null` THEN the system SHALL delete any existing rating for the triple and return `204`, and SHALL NOT store a null-valued row
9. WHEN that endpoint receives `value: 0` THEN the system SHALL store a rating of zero as a real value, distinct in every read from an absent rating (the null-vs-zero trap that frontend `rankSquad` depends on)
10. WHEN that endpoint receives a non-integer or a value outside 0–10 THEN the system SHALL return `400`
11. WHEN `eventType` is neither `training` nor `game`, or the named event does not exist or is not the caller's THEN the system SHALL return `400`

**Independent Test**: Set a rating to 5, then to 0, then to null → three distinct observable states, the last leaving no row.

---

### P8: Competitions and opponents ⭐ MVP

**User Story**: As a coach, I want managed lists of competitions and opponents
whose renames follow through to my fixtures, so `competitionService` and
`opponentService` have a backend.

**Acceptance Criteria**:

1. WHEN `GET /api/v1/competitions` or `GET /api/v1/opponents` is called THEN the system SHALL return the caller's list ordered case-insensitively by name
2. WHEN one is created with a name that trims to empty THEN the system SHALL return `400`
3. WHEN one is created with a name matching an existing entry case-insensitively after trimming THEN the system SHALL return `409`, enforced by a database unique index on `(owner_id, lower(name))` and not by application logic alone
4. WHEN one is created THEN the system SHALL store the **trimmed** name and return it
5. WHEN `PATCH /api/v1/competitions/{id}` renames an entry THEN the system SHALL, in one transaction, update the entry and rewrite `games.competition` on every one of the caller's games whose current value matches the old name case-insensitively after trimming (AD-104)
6. WHEN that same rename is applied to opponents THEN the system SHALL cascade to `games.opponent` and SHALL NOT touch any standings rival row that happens to share the name — a rival row is a separate model (frontend AD-010)
7. WHEN the cascade fails partway THEN the system SHALL roll back the rename entirely, leaving no game carrying a name whose list entry no longer exists
8. WHEN a rename resolves to the same trimmed name as before THEN the system SHALL perform no cascade and return `200`
9. WHEN `DELETE /api/v1/competitions/{id}` is called THEN the system SHALL delete the list entry and SHALL leave the name in place on historical games — the fixture happened (AD-104)

**Independent Test**: Create a competition, attach it to two games, rename it → both games carry the new name; delete it → both games keep it.

---

### P9: Uniform error contract ⭐ MVP

**User Story**: As the frontend developer, I want every failure in one
machine-readable shape, so `NotFoundError`/`ValidationError` can be
reconstructed without parsing English.

**Acceptance Criteria**:

1. WHEN any endpoint fails THEN the system SHALL return `application/problem+json` per RFC 9457 with `type`, `title`, `status`, `detail` and `instance` (AD-109)
2. WHEN a request body fails bean validation THEN the system SHALL return `400` with an `errors` object keyed by field name, each value a human-readable message
3. WHEN an unhandled exception occurs THEN the system SHALL return `500` with a generic `detail`, log the stack trace with a correlation id, and SHALL NOT leak the exception class, message or any SQL into the response
4. WHEN the database is unavailable mid-request THEN the system SHALL return `503` with problem type `database-unavailable` rather than a `500`
5. WHEN two clients update the same entity and the second holds a stale version THEN the system SHALL return `409` with problem type `stale-version`
6. WHEN a path variable cannot be parsed as a UUID THEN the system SHALL return `400`, not `500`

**Independent Test**: A malformed create body returns `400` with per-field messages; a bogus UUID returns `400`.

---

### P10: API documentation

**Why P2**: Valuable for the frontend rewiring, not required for the API to work.

**Acceptance Criteria**:

1. WHEN the service runs THEN `GET /swagger-ui.html` SHALL serve interactive documentation covering every endpoint
2. WHEN `GET /v3/api-docs` is called THEN the system SHALL return an OpenAPI 3.1 document including auth schemes, request/response schemas and error shapes

---

### P11: Granular exercise sub-resources

**Why P3**: The frontend submits whole trainings; these endpoints have no caller today.

**Acceptance Criteria**:

1. WHEN `POST /api/v1/trainings/{id}/exercises` is called THEN the system SHALL append one exercise and return `201`
2. WHEN `PATCH`/`DELETE /api/v1/trainings/{id}/exercises/{exerciseId}` is called THEN the system SHALL update or remove that exercise and re-pack the remaining order indexes contiguously

---

### P12: API container image

**Why P3**: The database in Docker was the stated requirement; containerising the API is a deployment convenience.

**Acceptance Criteria**:

1. WHEN `docker compose up` runs THEN both the API and the database SHALL start, with the API waiting on the database's healthcheck
2. WHEN the image is built THEN it SHALL use a multi-stage build and a non-root runtime user

---

## Endpoint Inventory

Every endpoint, mapped to the frontend service method it replaces. All paths are
prefixed `/api/v1`. **48 endpoints.**

### Auth & profile — replaces `context/AuthContext.jsx`

| Method | Path | Replaces | Req ID |
| --- | --- | --- | --- |
| POST | `/auth/register` | `signUp` | AUTH-01 |
| POST | `/auth/login` | `signIn` | AUTH-02 |
| POST | `/auth/refresh` | *(new — no mock equivalent)* | AUTH-04 |
| POST | `/auth/logout` | `signOut` | AUTH-05 |
| GET | `/users/me` | `user` state | AUTH-06 |
| PATCH | `/users/me` | `updateProfile` | AUTH-06 |
| PUT | `/users/me/password` | `changePassword` | AUTH-07 |

### Teams & players — replaces `teamService`

| Method | Path | Replaces | Req ID |
| --- | --- | --- | --- |
| GET | `/teams` | `getAll` | TEAM-01 |
| POST | `/teams` | `create` | TEAM-02 |
| GET | `/teams/{id}` | `getById` | TEAM-01 |
| PATCH | `/teams/{id}` | `update` | TEAM-03 |
| DELETE | `/teams/{id}` | `delete` | TEAM-04 |
| GET | `/teams/{teamId}/players` | *(nested in team today)* | PLAY-01 |
| POST | `/teams/{teamId}/players` | `addPlayer` | PLAY-02 |
| GET | `/teams/{teamId}/players/{playerId}` | *(new)* | PLAY-01 |
| PATCH | `/teams/{teamId}/players/{playerId}` | `updatePlayer` | PLAY-03 |
| DELETE | `/teams/{teamId}/players/{playerId}` | `deletePlayer` | PLAY-04 |

### Trainings & exercises — replaces `trainingService`

| Method | Path | Replaces | Req ID |
| --- | --- | --- | --- |
| GET | `/trainings` | `getAll` / `getAllNumbered` | TRAIN-01, TRAIN-02 |
| GET | `/trainings?teamId=` | `getAllNumbered(teamId)` | TRAIN-02 |
| GET | `/trainings?assigned=false` | `getUnassigned` | TRAIN-03 |
| POST | `/trainings` | `create` | TRAIN-04 |
| GET | `/trainings/{id}` | `getById` | TRAIN-01 |
| PATCH | `/trainings/{id}` | `update` | TRAIN-05 |
| DELETE | `/trainings/{id}` | `delete` | TRAIN-06 |
| POST | `/trainings/{id}/exercises` | *(P3)* | EXER-02 |
| PATCH | `/trainings/{id}/exercises/{exerciseId}` | *(P3)* | EXER-02 |
| DELETE | `/trainings/{id}/exercises/{exerciseId}` | *(P3)* | EXER-02 |

### Games & standings — replaces `gameService`, `standingsService`

| Method | Path | Replaces | Req ID |
| --- | --- | --- | --- |
| GET | `/games` | `getAll` | GAME-01 |
| GET | `/games?teamId=` | `getAll(teamId)` | GAME-01 |
| GET | `/games?status=scheduled` | `getScheduled` | GAME-02 |
| GET | `/games?status=played` | `getPlayed` | GAME-02 |
| GET | `/games?assigned=false` | `getUnassigned` | GAME-03 |
| POST | `/games` | `create` | GAME-04 |
| GET | `/games/{id}` | *(new)* | GAME-01 |
| PATCH | `/games/{id}` | `update` | GAME-04 |
| DELETE | `/games/{id}` | `delete` | GAME-07 |
| PUT | `/games/{id}/result` | `recordResult` | GAME-05 |
| DELETE | `/games/{id}/result` | `clearResult` | GAME-06 |
| GET | `/standings/rivals` | `standingsService.getAll` | STAND-01 |
| POST | `/standings/rivals` | `create` | STAND-02 |
| PATCH | `/standings/rivals/{id}` | `update` | STAND-02 |
| DELETE | `/standings/rivals/{id}` | `delete` | STAND-03 |
| GET | `/standings?teamId=` | `lib/standings.js` (server-side) | STAND-04 |

### Cards & ratings — replaces `cardService`, `ratingService`

| Method | Path | Replaces | Req ID |
| --- | --- | --- | --- |
| GET | `/cards` | `getAll` / `getByGame` / `getByPlayer` | CARD-01 |
| POST | `/cards` | `record` | CARD-02 |
| DELETE | `/cards/{id}` | `remove` | CARD-03 |
| GET | `/ratings` | `getAll` / `getByEvent` / `getByPlayer` | RATE-01 |
| PUT | `/ratings/{eventType}/{eventId}/players/{playerId}` | `setRating` | RATE-02, RATE-03 |
| DELETE | `/ratings/{id}` | `remove` | RATE-04 |

### Reference lists — replaces `competitionService`, `opponentService`

| Method | Path | Replaces | Req ID |
| --- | --- | --- | --- |
| GET | `/competitions` | `getAll` | COMP-01 |
| POST | `/competitions` | `create` | COMP-02 |
| PATCH | `/competitions/{id}` | `update` (rename cascade) | COMP-03 |
| DELETE | `/competitions/{id}` | `delete` | COMP-04 |
| GET | `/opponents` | `getAll` | OPP-01 |
| POST | `/opponents` | `create` | OPP-02 |
| PATCH | `/opponents/{id}` | `update` (rename cascade) | OPP-03 |
| DELETE | `/opponents/{id}` | `delete` | OPP-04 |

### Operations

| Method | Path | Purpose | Req ID |
| --- | --- | --- | --- |
| GET | `/actuator/health` | Liveness + DB connectivity | INFRA-04 |
| GET | `/actuator/info` | Build/version metadata | INFRA-05 |
| GET | `/swagger-ui.html` | Interactive docs (P2) | DOC-01 |
| GET | `/v3/api-docs` | OpenAPI 3.1 (P2) | DOC-01 |

**Frontend service methods with no endpoint — deliberate:**
`store.reset()` (a localStorage concern; a dev-profile-only `POST /dev/reset`
may be added during execution but is not specified here),
`cardService.removeByGame`/`removeByPlayer` and
`ratingService.removeByEvent`/`removeByPlayer` (hand-wired frontend cascades,
replaced by database FK actions per AD-106/AD-107 — they must NOT become
endpoints, or the cascade gains a second, forgettable path).

---

## Edge Cases

- WHEN a training's `day` is an invalid date THEN the system SHALL reject it at `400`, rather than replicating the frontend's `Infinity` sort fallback
- WHEN two trainings for one team share the exact same `day` THEN numbering SHALL break the tie by id, deterministically across repeated reads
- WHEN a game is deleted while a rating for it is being written THEN the write SHALL fail with `400` (unknown event), never orphan a row
- WHEN a rival standings row is created with `played: 0` and all-zero figures THEN the system SHALL accept it — a team that has not played is valid
- WHEN a diagram's shape coordinates fall outside 0–1 THEN the system SHALL clamp them to the pitch bounds on write, mirroring the frontend's `clampToPitch`, rather than rejecting the payload
- WHEN `GET /standings?teamId=` names a team with no games and no rival rows THEN the system SHALL return a single all-zero row for that team, not an empty array
- WHEN a JWT is signed with the right algorithm but a different key THEN the system SHALL return `401`, never `500`
- WHEN a request body contains unknown JSON fields THEN the system SHALL ignore them, not reject the request
- WHEN a `PATCH` body is entirely empty THEN the system SHALL return `200` with the unchanged entity, not `400`

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| INFRA-01 Gradle/Kotlin/Boot skeleton | P1 | 0 | Pending |
| INFRA-02 Docker Compose Postgres 18 | P1 | 0 | Pending |
| INFRA-03 Flyway `V1__init.sql` | P1 | 0 | Pending |
| INFRA-04 Health endpoint + fail-fast | P1 | 0 | Pending |
| INFRA-05 Actuator + structured logging | P1 | 0 | Pending |
| INFRA-06 Testcontainers harness | P1 | 0 | Pending |
| DATA-01 Schema: users, teams, players | P1 | 1 | Pending |
| DATA-02 Schema: trainings, exercises | P1 | 1 | Pending |
| DATA-03 Schema: games, standings | P1 | 1 | Pending |
| DATA-04 Schema: cards, ratings (AD-106) | P1 | 1 | Pending |
| DATA-05 Schema: competitions, opponents | P1 | 1 | Pending |
| DATA-06 FK actions & cascades (AD-107) | P1 | 1 | Pending |
| ERR-01…06 Problem+JSON contract | P9 | 2 | Pending |
| AUTH-01…07 Register/login/refresh/profile | P2, P3 | 3 | Pending |
| OWN-01 Ownership scoping on every query | P2 | 3 | Pending |
| OWN-02 Cascade rules verified | P4 | 3 | Pending |
| TEAM-01…04 Team endpoints | P4 | 4 | Pending |
| PLAY-01…04 Player endpoints | P4 | 4 | Pending |
| TRAIN-01…06 Training endpoints | P5 | 5 | Pending |
| EXER-01 Nested exercises + diagram jsonb | P5 | 5 | Pending |
| EXER-02 Exercise sub-resources | P11 | 9 | Pending |
| GAME-01…07 Game endpoints | P6 | 6 | Pending |
| STAND-01…04 Standings endpoints | P6 | 6 | Pending |
| CARD-01…03 Card endpoints | P7 | 7 | Pending |
| RATE-01…04 Rating endpoints | P7 | 7 | Pending |
| COMP-01…04 Competition endpoints | P8 | 8 | Pending |
| OPP-01…04 Opponent endpoints | P8 | 8 | Pending |
| DOC-01 OpenAPI/Swagger | P10 | 9 | Pending |
| DEPLOY-01 API Dockerfile | P12 | 9 | Pending |

**Coverage:** 30 requirement groups, 30 mapped to tasks, 0 unmapped.

---

## Success Criteria

- [ ] All 48 endpoints implemented and integration-tested against real PostgreSQL
- [ ] `docker compose up -d db && ./gradlew build` is green from a clean clone
- [ ] Every frontend service method has a documented HTTP equivalent — verified by walking `spec.md`'s inventory against the frontend's 8 service files
- [ ] An ownership isolation test proves user B receives `404` for every one of user A's entity types
- [ ] Cascade tests prove: delete team → players/cards/ratings gone, games/trainings survive with null `teamId`; delete game → cards/ratings gone; delete training → exercises/ratings gone
- [ ] The null-vs-zero trap is covered by explicit tests in three places: `0-0` game results, `value: 0` ratings, and all-zero standings rows
- [ ] The competition/opponent rename cascade is proven transactional by a test that forces a mid-cascade failure and asserts full rollback
