# Coach Planner API — Plan

Kotlin backend for the [coach-planner](../../coach-planner) React app, which
today keeps every team, player, training, game, card and rating in
`localStorage` behind a mock auth context.

**Status:** planned, not started. No code exists yet.

## Documents

| File | What it holds |
| --- | --- |
| [STATE.md](STATE.md) | Ten architecture decisions (AD-101…AD-110) with reasons and trade-offs, plus the handoff snapshot |
| [features/00-backend-mvp/spec.md](features/00-backend-mvp/spec.md) | WHAT: 12 user stories, ~120 acceptance criteria, the **48-endpoint inventory** mapped to the frontend service methods each replaces |
| [features/00-backend-mvp/design.md](features/00-backend-mvp/design.md) | HOW: package layout, **full PostgreSQL DDL**, wire formats, Docker Compose, testing strategy, risk table |
| [features/00-backend-mvp/tasks.md](features/00-backend-mvp/tasks.md) | **49 atomic tasks** across 10 phases, each with files, tests, gate and dependencies |

## Locked decisions

| | |
| --- | --- |
| **Stack** | Kotlin 2.4 · Spring Boot 4.1 · Spring Data JPA · Flyway · Java 21 |
| **Database** | PostgreSQL 18, in Docker via `docker-compose.yml` |
| **Auth** | Real JWT auth with per-user data ownership on every entity |
| **Reference lists** | Competitions/opponents stay name strings with rename cascade — the frontend's AD-010 preserved |
| **Round scope** | Backend + Docker only; rewiring the React app is a separate feature |

## Phases

```
0 Foundation & Docker      T1–T5    ← T1 is a risk spike; gate on it
1 Schema & persistence     T6–T10
2 Error contract           T11–T13
3 Auth & ownership         T14–T21  ← everything below depends on this
   ├─ 4 Teams & players    T22–T26
   ├─ 5 Trainings          T27–T31
   ├─ 6 Games & standings  T32–T37
   ├─ 7 Cards & ratings    T38–T42
   └─ 8 Reference lists    T43–T46
9 Docs & packaging         T47–T49  (P2/P3)
```

Phases 4–8 are independent of each other and can be reordered.

## Endpoints at a glance

| Area | Count | Replaces |
| --- | --- | --- |
| Auth & profile | 7 | `context/AuthContext.jsx` |
| Teams & players | 10 | `teamService` |
| Trainings & exercises | 10 | `trainingService` |
| Games & standings | 16 | `gameService`, `standingsService` |
| Cards & ratings | 6 | `cardService`, `ratingService` |
| Competitions & opponents | 8 | `competitionService`, `opponentService` |
| Operations | 4 | — |

Four frontend cascade helpers (`removeByGame`, `removeByPlayer`,
`removeByEvent`) deliberately get **no** endpoint — they become database FK
actions instead (AD-106, AD-107).

## Known gaps, recorded rather than hidden

1. **Rival standings rows have no team.** The frontend's `standings` records
   carry no `teamId`, so a coach with two teams shares one league table.
   Preserved rather than silently fixed, because fixing it changes frontend
   behaviour. Logged as an assumption in `spec.md`.
2. **No frontend rewiring.** Replacing the 8 services with `fetch`, token
   storage, and `Date` re-hydration needs its own feature in the frontend repo.
3. **No `localStorage` import.** Existing browser data does not migrate.

## Getting started (once Phase 0 lands)

```bash
cp .env.example .env && docker compose up -d db && ./gradlew build
```
