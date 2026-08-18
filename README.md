# Coach Planner API

Kotlin backend for [Coach Planner](../coach-planner) — a React app for football
coaches to manage teams, players, trainings, games and a calendar. This service
replaces the frontend's `localStorage`-backed mock store with a real HTTP API,
PostgreSQL database and JWT authentication: teams & players, trainings &
exercises (with diagrams), games & standings, cards & ratings, and managed
reference lists (competitions/opponents) with rename cascades.

**Status:** feature-complete. Every endpoint the frontend's services need has
a tested backend — see [.specs/](.specs/README.md) for the full specification,
schema, task breakdown and per-phase verification reports.

## Stack

- Kotlin 2.3.21 · Spring Boot 4.1.0 (Spring Data JPA, Spring Security, Validation, Actuator)
- PostgreSQL 18, migrated with Flyway
- Gradle (Kotlin DSL), Java 21 toolchain
- Docker Compose for local Postgres (and, optionally, the API itself)
- Testcontainers for integration tests (real Postgres, no H2)
- springdoc-openapi for interactive API docs

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

The API listens on `http://localhost:8080`. Interactive docs are at
`http://localhost:8080/swagger-ui.html`, and the raw OpenAPI 3.1 document at
`http://localhost:8080/v3/api-docs`.

### Running the API itself in Docker

`docker compose up -d db` above only starts the database — that's the
default local-dev path (run the API with `bootRun` against it). To run the
whole stack in containers instead:

```bash
cp .env.example .env   # make sure JWT_SECRET is set too — see the file's comment
docker compose --profile full up -d --build
curl http://localhost:8080/actuator/health
```

## Development

```bash
./gradlew test           # unit + integration tests (spins up Testcontainers)
./gradlew bootRun         # run the API against docker compose's db
docker compose down -v    # stop and wipe the local database
```

## Project layout

```
.specs/                          plan: decisions, spec, design, tasks, per-phase validation reports
src/main/kotlin/com/coachplanner/api/
  auth/                          users, JWT, refresh tokens
  team/                          teams, players
  training/                     trainings, exercises, diagrams
  game/                          games
  standings/                     rival rows, table calculation
  discipline/                    cards, ratings
  reference/                     competitions, opponents
  common/                        error handling, ownership, ids
  config/                        security, Jackson, OpenAPI, logging
src/main/resources/
  db/migration/                  Flyway SQL migrations
  application*.yml                config per profile
Dockerfile                        multi-stage build for the API image (non-root runtime user)
docker-compose.yml                local PostgreSQL (+ API, behind the `full` profile)
```

## Related

- [coach-planner](../coach-planner) — the React frontend this API serves
- [.specs/README.md](.specs/README.md) — plan index: decisions, endpoint inventory, phases
