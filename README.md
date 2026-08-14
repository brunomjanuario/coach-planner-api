# Coach Planner API

Kotlin backend for [Coach Planner](../coach-planner) — a React app for football
coaches to manage teams, players, trainings, games and a calendar. This service
replaces the frontend's current `localStorage`-backed mock store with a real
HTTP API, PostgreSQL database and JWT authentication.

**Status:** planned, not implemented yet. See [.specs/](.specs/README.md) for
the full specification, schema and task breakdown before writing any code.

## Stack

- Kotlin 2.4 · Spring Boot 4.1 (Spring Data JPA, Spring Security, Validation, Actuator)
- PostgreSQL 18, migrated with Flyway
- Gradle (Kotlin DSL), Java 21 toolchain
- Docker Compose for local Postgres
- Testcontainers for integration tests (real Postgres, no H2)

See [.specs/features/00-backend-mvp/design.md](.specs/features/00-backend-mvp/design.md)
for the full architecture, database schema and rationale.

## Getting started

Requires Docker and JDK 21.

```bash
cp .env.example .env
docker compose up -d db
./gradlew build
./gradlew bootRun
```

The API listens on `http://localhost:8080`. Interactive docs (once implemented)
are at `http://localhost:8080/swagger-ui.html`.

## Development

```bash
./gradlew test         # unit + integration tests (spins up Testcontainers)
./gradlew bootRun       # run the API against docker compose's db
docker compose down -v  # stop and wipe the local database
```

## Project layout

```
.specs/                          plan: decisions, spec, design, tasks
src/main/kotlin/com/coachplanner/api/
  auth/                          users, JWT, refresh tokens
  team/                          teams, players
  training/                     trainings, exercises, diagrams
  game/                          games
  standings/                     rival rows, table calculation
  discipline/                    cards, ratings
  reference/                     competitions, opponents
  common/                        error handling, ownership, ids
src/main/resources/
  db/migration/                  Flyway SQL migrations
  application*.yml                config per profile
docker-compose.yml                local PostgreSQL (+ API, behind a profile)
```

## Related

- [coach-planner](../coach-planner) — the React frontend this API serves
- [.specs/README.md](.specs/README.md) — plan index: decisions, endpoint inventory, phases
