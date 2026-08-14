# Coach Planner API — Design

**Spec:** `.specs/features/00-backend-mvp/spec.md`
**Decisions:** `.specs/STATE.md` (AD-101 … AD-110)
**Status:** Ready for tasks

---

## Stack (verified 2026-08-14)

| Component | Version | Note |
| --- | --- | --- |
| Kotlin | 2.4.0 | Current stable (2026-06). 2.4.20 due Sept 2026. |
| Spring Boot | 4.1.0 | Released 2026-06-11. OSS support to 2027-07-31. |
| Spring Framework | 7.0.x | Transitive via Boot 4.1. |
| Spring Security | 7.1.x | Transitive. OAuth2 resource server for JWT. |
| Hibernate | 7.4.x | Transitive. Jakarta EE 11. |
| Java toolchain | 21 (LTS) | Boot 4.1 minimum is 17; supports up to 26. |
| PostgreSQL | 18 | `postgres:18-alpine`. 19 is beta until Sept 2026 — not used. |
| Flyway | Boot-managed | Version comes from the Boot BOM. |
| Testcontainers | 2.x + `spring-boot-testcontainers:4.1.0` | See risk below. |
| springdoc-openapi | latest Boot-4-compatible | **Must be version-checked before adding** (P2). |

### Spring Boot 4 gotchas that will bite (AD-101)

These are not optional trivia — each one breaks a copy-pasted 3.x tutorial:

- Web starter is **`spring-boot-starter-webmvc`**, not `spring-boot-starter-web`
- **Jackson 3**: group id, package names and several class names changed. Kotlin
  module is `tools.jackson.module:jackson-module-kotlin`-shaped, not
  `com.fasterxml.*`. Verify the coordinate at task time; do not guess.
- **`@MockBean` / `@SpyBean` are removed** → use `@MockitoBean` / `@MockitoSpyBean`
- **Virtual threads are enabled by default** for the web thread pool. Anything
  using `ThreadLocal`-based state or synchronized blocks around I/O must be
  checked. We have none, but transaction-bound `ThreadLocal`s in libraries are
  the classic pinning source.
- Jakarta EE 11 — `jakarta.*` everywhere, never `javax.*`

### Flagged risk (AD-110)

Testcontainers v2 against Spring Boot 4 has known breakage in *some* third-party
modules. The Postgres module plus Boot's own `spring-boot-testcontainers`
starter is the supported combination. **Phase 0 T1 exists specifically to prove
this boots before anything is built on it.** If it does not, the fallback is
pinning Testcontainers 1.20.x, or running a `docker compose` database for the
test profile — decide then, do not pre-optimise.

---

## Architecture

Layered, one package per bounded area. Deliberately boring — the interesting
decisions are in the schema, not the wiring.

```
com.coachplanner.api
├── CoachPlannerApplication.kt
├── config/
│   ├── SecurityConfig.kt          JWT filter chain, public-path allowlist
│   ├── JacksonConfig.kt           ISO-8601 instants, no null omission
│   └── OpenApiConfig.kt           (P2)
├── common/
│   ├── ApiExceptionHandler.kt     @RestControllerAdvice → ProblemDetail (AD-109)
│   ├── errors.kt                  NotFoundException, ValidationException, ConflictException
│   ├── OwnedRepository.kt         base interface: every finder takes ownerId
│   └── Ids.kt                     UUIDv7 generation (AD-105)
├── auth/
│   ├── User.kt  RefreshToken.kt
│   ├── UserRepository.kt  RefreshTokenRepository.kt
│   ├── AuthService.kt  JwtService.kt
│   ├── AuthController.kt  UserController.kt
│   └── dto/
├── team/          Team, Player, repositories, TeamService, TeamController, PlayerController, dto
├── training/      Training, Exercise, TrainingNumbering.kt, service, controller, dto
├── game/          Game, GameService, GameController, dto
├── standings/     RivalRow, StandingsCalculator.kt, service, controller, dto
├── discipline/    Card, Rating, services, controllers, dto
└── reference/     Competition, Opponent, ReferenceListService (shared), controllers, dto
```

**`ReferenceListService` is generic over both lists.** Competitions and opponents
differ only in which `games` column their rename cascades to. The frontend
learned this the expensive way — its AD-016 records two 226-line popups that
differed only in the noun, so every fix had to be written twice. One service
parameterised by the target column, not two near-identical ones.

### Ownership enforcement (OWN-01)

Ownership is enforced **in the query**, never by a post-fetch `if`:

```kotlin
fun findByIdAndOwnerId(id: UUID, ownerId: UUID): Team?
```

A post-fetch check is one forgotten `if` away from a data leak, and it returns
`403`-shaped information (the row existed) where we want `404`. Because the
owner is part of the `WHERE`, another user's id is simply not found — the
`404`-not-`403` rule from the spec falls out of the query shape rather than
being a policy someone has to remember.

`ownerId` comes from the JWT subject via an `@AuthenticationPrincipal`-resolved
argument. **No endpoint accepts an owner id from the request.**

Players, exercises, cards and ratings have no `owner_id` column — they are
reached only through an owned parent, and their queries join to it.

---

## Database schema

Postgres 18. One Flyway migration, `V1__init.sql`. Snake_case columns; JPA maps
via Spring's default naming strategy.

```sql
CREATE EXTENSION IF NOT EXISTS citext;

-- ─── auth ────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            uuid PRIMARY KEY,
    email         citext NOT NULL UNIQUE,
    name          text   NOT NULL CHECK (length(trim(name)) > 0),
    password_hash text   NOT NULL,
    version       bigint NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
-- citext gives case-insensitive uniqueness in the database, so
-- "A@b.com" and "a@B.com" cannot both register regardless of app code.

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,          -- SHA-256; never the raw token
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

-- ─── squad ───────────────────────────────────────────────────────────────
CREATE TABLE teams (
    id         uuid PRIMARY KEY,
    owner_id   uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       text NOT NULL CHECK (length(trim(name)) > 0),
    club       text,
    season     text,
    version    bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_teams_owner ON teams(owner_id);

CREATE TYPE player_position AS ENUM (
    'GK','RB','CB','LB','RWB','LWB','CDM','RM','CM','LM','CAM','RW','LW','ST','CF','RF','LF'
);

CREATE TABLE players (
    id             uuid PRIMARY KEY,
    team_id        uuid NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    name           text NOT NULL CHECK (length(trim(name)) > 0),
    age            int  CHECK (age BETWEEN 4 AND 99),
    shirt_number   int  CHECK (shirt_number BETWEEN 1 AND 99),
    position       player_position,
    goals          int NOT NULL DEFAULT 0 CHECK (goals >= 0),
    assists        int NOT NULL DEFAULT 0 CHECK (assists >= 0),
    conceded_goals int NOT NULL DEFAULT 0 CHECK (conceded_goals >= 0),
    version        bigint NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_players_team ON players(team_id);
-- Deliberately NO unique index on (team_id, shirt_number): the frontend's own
-- seed data ships three players wearing 3 in one team. Enforcing it here would
-- reject data the app already considers valid.

-- ─── trainings ───────────────────────────────────────────────────────────
CREATE TABLE trainings (
    id               uuid PRIMARY KEY,
    owner_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id          uuid REFERENCES teams(id) ON DELETE SET NULL,   -- AD-107
    day              timestamptz NOT NULL,
    duration_minutes int NOT NULL CHECK (duration_minutes BETWEEN 1 AND 480),
    version          bigint NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainings_owner_team_day ON trainings(owner_id, team_id, day);
-- Composite ordered exactly as the numbering read path scans (AD-108).

CREATE TABLE exercises (
    id                uuid PRIMARY KEY,
    training_id       uuid NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    order_index       int  NOT NULL CHECK (order_index >= 0),
    description       text NOT NULL CHECK (length(trim(description)) > 0),
    number_of_players int  CHECK (number_of_players BETWEEN 1 AND 40),
    duration_minutes  int  CHECK (duration_minutes BETWEEN 1 AND 480),
    repetitions       int  CHECK (repetitions BETWEEN 1 AND 99),
    diagram           jsonb,                                          -- AD-102, frontend AD-015
    UNIQUE (training_id, order_index) DEFERRABLE INITIALLY DEFERRED
);
-- DEFERRABLE so a wholesale reorder inside one transaction doesn't trip the
-- constraint mid-update. The legacy `image` field is NOT carried over: the
-- frontend's AD-015 left it deliberately unused and dead columns rot.

-- ─── games ───────────────────────────────────────────────────────────────
CREATE TABLE games (
    id          uuid PRIMARY KEY,
    owner_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id     uuid REFERENCES teams(id) ON DELETE SET NULL,          -- AD-107
    opponent    text NOT NULL CHECK (length(trim(opponent)) > 0),      -- AD-104: a name, not an FK
    competition text,                                                  -- AD-104
    date        timestamptz NOT NULL,
    is_home     boolean NOT NULL,
    us_score    int CHECK (us_score BETWEEN 0 AND 99),
    them_score  int CHECK (them_score BETWEEN 0 AND 99),
    version     bigint NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT scores_recorded_together
        CHECK ((us_score IS NULL) = (them_score IS NULL))
);
CREATE INDEX idx_games_owner_team_date ON games(owner_id, team_id, date DESC);
CREATE INDEX idx_games_owner_competition ON games(owner_id, lower(competition));
CREATE INDEX idx_games_owner_opponent    ON games(owner_id, lower(opponent));
-- The two lower() indexes serve the AD-104 rename cascade, which matches
-- case-insensitively on trimmed names.
-- `scores_recorded_together` makes the null-vs-zero trap unrepresentable: a
-- half-recorded result cannot exist, so `hasResult` is a single null check.

-- ─── standings (manually maintained rival rows) ─────────────────────────
CREATE TABLE standings_rivals (
    id            uuid PRIMARY KEY,
    owner_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          text NOT NULL CHECK (length(trim(name)) > 0),
    played        int NOT NULL CHECK (played  >= 0),
    won           int NOT NULL CHECK (won     >= 0),
    drawn         int NOT NULL CHECK (drawn   >= 0),
    lost          int NOT NULL CHECK (lost    >= 0),
    goals_for     int NOT NULL CHECK (goals_for     >= 0),
    goals_against int NOT NULL CHECK (goals_against >= 0),
    version       bigint NOT NULL DEFAULT 0,
    CONSTRAINT results_sum_to_played CHECK (won + drawn + lost = played)
);
CREATE INDEX idx_standings_owner ON standings_rivals(owner_id);
-- No points/goal_difference columns — always derived (AD-108, frontend AD-008).
-- No team_id — see spec.md's logged assumption. This is the known gap.

-- ─── discipline ──────────────────────────────────────────────────────────
CREATE TYPE card_type AS ENUM ('yellow','red');

CREATE TABLE cards (
    id         uuid PRIMARY KEY,
    player_id  uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    game_id    uuid NOT NULL REFERENCES games(id)   ON DELETE CASCADE,
    type       card_type NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_cards_game   ON cards(game_id);
CREATE INDEX idx_cards_player ON cards(player_id);

CREATE TABLE ratings (
    id          uuid PRIMARY KEY,
    player_id   uuid NOT NULL REFERENCES players(id)   ON DELETE CASCADE,
    training_id uuid REFERENCES trainings(id) ON DELETE CASCADE,       -- AD-106
    game_id     uuid REFERENCES games(id)     ON DELETE CASCADE,       -- AD-106
    value       smallint NOT NULL CHECK (value BETWEEN 0 AND 10),
    version     bigint NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT exactly_one_event CHECK ((training_id IS NULL) != (game_id IS NULL))
);
CREATE UNIQUE INDEX uq_ratings_player_training
    ON ratings(player_id, training_id) WHERE training_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ratings_player_game
    ON ratings(player_id, game_id)     WHERE game_id IS NOT NULL;
-- Two partial unique indexes give the (player, eventType, eventId) uniqueness
-- the frontend enforces with a findIndex. This is what makes the upsert safe
-- under concurrent calls (AC RATE-07) — the database rejects the duplicate,
-- the service retries as an update.

-- ─── reference lists (AD-104) ────────────────────────────────────────────
CREATE TABLE competitions (
    id       uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     text NOT NULL CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX uq_competitions_owner_name ON competitions(owner_id, lower(name));

CREATE TABLE opponents (
    id       uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     text NOT NULL CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX uq_opponents_owner_name ON opponents(owner_id, lower(name));
-- The expression index is why AC COMP-03 says "enforced by a database unique
-- index, not application logic alone": the frontend's normalize()+some() check
-- has a TOCTOU race that a unique index does not.
```

### Cascade matrix (AD-107) — the behaviour tests must prove

| Delete | Cascades to | Survives |
| --- | --- | --- |
| `users` | everything owned | — |
| `teams` | players → their cards + ratings | trainings & games (`team_id` → NULL) |
| `players` | that player's cards + ratings | — |
| `trainings` | exercises + that training's ratings | — |
| `games` | cards + that game's ratings | — |
| `competitions` / `opponents` | nothing | the name stays on historical games |

Every row here is a database FK action, not service code. The frontend
hand-wires the equivalent across four `removeBy*` helpers called from three
services — that is exactly the shape of cascade that gets forgotten at the
fourth call site.

---

## Wire format

Camel-case JSON matching the frontend's stored records, so the swap needs no
translation layer.

```jsonc
// Team — players nested, matching frontend Team.players[]
{ "id": "018f…", "name": "Sub-11", "club": "Amadora", "season": "23/24",
  "players": [ { "id": "018f…", "teamId": "018f…", "name": "João", "age": 15,
                 "shirtNumber": 1, "position": "CAM",
                 "goals": 3, "assists": 1, "concededGoals": 0 } ] }

// Training — `number` computed on read (AD-108), `day` as an ISO instant
{ "id": "018f…", "teamId": "018f…", "number": 2,
  "day": "2024-10-24T15:00:00Z", "duration": 90,
  "exercises": [ { "id": "018f…", "trainingId": "018f…", "description": "SSG",
                   "numberOfPlayers": 21, "duration": 20, "repetitions": 2,
                   "diagram": null } ] }

// Game — scores explicitly null when unplayed; outcome is NOT sent (AD-108)
{ "id": "018f…", "teamId": "018f…", "opponent": "Benfica",
  "competition": "District League", "date": "2030-01-01T15:00:00Z",
  "isHome": true, "usScore": null, "themScore": null }

// Rating — eventType/eventId derived from the FK columns (AD-106)
{ "id": "018f…", "playerId": "018f…", "eventType": "game",
  "eventId": "018f…", "value": 7 }

// Error — RFC 9457 (AD-109)
{ "type": "https://coachplanner.dev/problems/validation-failed",
  "title": "Validation failed", "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/teams",
  "errors": { "name": "must not be blank" } }
```

Two mapping rules that are easy to get wrong and must be tested directly:

1. `duration_minutes` (column) ↔ `duration` (JSON). The frontend calls it
   `duration` on both Training and Exercise; the column is explicit about units.
2. `order_index` is never serialized. Exercise order is conveyed by array
   position alone, which is what the frontend already relies on.

---

## Derived-value algorithms (AD-108)

**Training `number`** — port of the frontend's `numberTrainings`: group by
`team_id`, order by `day` ascending with ties broken by id string comparison,
assign 1-based positions. `team_id IS NULL` → `number: null`. The critical
property, and the one the spec makes an AC: numbers are assigned across the
team's *whole* history, then filtered — so `?teamId=` returns the same numbers
as the unfiltered call. Computing after filtering would renumber from 1.

**Standings** — port of `computeOurRow` + `toStandingsRow` + `sortStandings`.
Only games with both scores count. `points = won*3 + drawn`,
`goalDifference = goalsFor - goalsAgainst`, both always derived. Sort by points,
then goal difference, then goals for, then name. A team with no played games
gets an all-zero row, never absent.

**Rating aggregates** (`average`, `form`, `rankSquad`) stay in the frontend.
They are pure functions over already-fetched arrays and are already tested
there; moving them would duplicate the logic across the network boundary for no
gain. The frontend's own open item — whether `ratingService` should gain a batch
aggregation method — is unresolved there and is not resolved here.

---

## Docker

`docker-compose.yml` at the repo root. The database is the requirement; the API
service is P3 and lives behind a compose profile so `up -d db` stays the default
development path.

```yaml
services:
  db:
    image: postgres:18-alpine
    container_name: coach-planner-db
    environment:
      POSTGRES_DB:       ${POSTGRES_DB:-coachplanner}
      POSTGRES_USER:     ${POSTGRES_USER:-coachplanner}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-coachplanner} -d ${POSTGRES_DB:-coachplanner}"]
      interval: 5s
      timeout: 5s
      retries: 10
    restart: unless-stopped

  api:                      # P3 — `docker compose --profile full up`
    profiles: ["full"]
    build: .
    depends_on:
      db: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB:-coachplanner}
    ports:
      - "8080:8080"

volumes:
  pgdata:
```

- `POSTGRES_PASSWORD` uses `:?` so compose **fails loudly** on a missing `.env`
  rather than starting a database with a blank password.
- A named volume, not a bind mount — bind mounts on macOS are slow and leak
  root-owned files into the working tree.
- `.env.example` is committed; `.env` is gitignored.
- No init SQL scripts: Flyway owns the schema, and two migration mechanisms is
  one too many.

---

## Testing strategy

| Layer | Tool | Covers |
| --- | --- | --- |
| Pure logic | JUnit 5 + Kotest assertions | Training numbering, standings maths, diagram validation |
| Repository/schema | `@DataJpaTest` + Testcontainers | FK cascades, CHECK constraints, partial unique indexes |
| Endpoint | `@SpringBootTest` + `MockMvc` + Testcontainers | Every endpoint: success, validation, ownership, cascade |
| Security | `@SpringBootTest` | Token lifecycle, reuse detection, public-path allowlist |

**Constraints get tests of their own.** `exactly_one_event`,
`scores_recorded_together`, `results_sum_to_played` and both partial unique
indexes are asserted by attempting the illegal write directly against the
repository and expecting a constraint violation. No application path should
ever produce these — which is precisely why they need a test that bypasses the
application path.

**Ownership isolation** gets one parameterised test walking every entity type
with two users, asserting `404` for every cross-user read and write. One test,
every endpoint, no per-endpoint copy to forget.

---

## What could go wrong

| Risk | Mitigation |
| --- | --- |
| Testcontainers v2 × Boot 4 incompatibility (AD-110) | Phase 0 T1 is a spike that proves it before any domain code exists. Fallback: pin Testcontainers 1.20.x or use a compose-provided test database. |
| Jackson 3 coordinate/package churn breaks Kotlin data-class serialization | Phase 0 T4 serializes a nullable-field DTO round-trip as its gate. Verify coordinates at task time; never copy from a 3.x tutorial. |
| Postgres enums (`player_position`, `card_type`) are awkward to evolve and need explicit Hibernate mapping | Accepted for two closed, product-defined sets. If a third, more volatile enum appears, use a `text` + `CHECK` instead. |
| The rename cascade (AD-104) silently half-applies | `@Transactional` plus an explicit rollback test that forces a mid-cascade failure — an AC, not a nice-to-have. |
| `citext` extension unavailable on a managed Postgres later | `postgres:18-alpine` ships it. If a host lacks it, fall back to a `lower(email)` unique index — a one-line migration. |
| Ownership check forgotten on a new endpoint | Ownership lives in the repository method signature, so a query without `ownerId` does not compile against `OwnedRepository`. |
