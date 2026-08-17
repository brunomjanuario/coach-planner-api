# Validation Report — `00-backend-mvp`

## Phase 5: Trainings & exercises (T27–T31)

**Verdict: PASS ✅**

**Commit range covered:** `00ec7e1..d0789bf` (`d86b541`, `4667c06`, `a392da4`, `d88cef9`, `3106008`, `d0789bf`)

**Verifier:** fresh sub-agent, author ≠ verifier, evidence-or-zero methodology.

---

### Spec-anchored AC coverage

| AC | Requirement (paraphrased) | Evidence (file:line) | Assertion matches spec outcome? |
| --- | --- | --- | --- |
| TRAIN-01 | `GET /trainings` returns caller's trainings, each with computed `number` and `exercises[]` in stored order | `TrainingReadsIT.kt:137-143` — `jsonPath("$.number").value(1)`, `jsonPath("$.exercises[0].description").value("Warm-up")`, `jsonPath("$.exercises[1].description").value("Rondo")`; also `TrainingControllerIT.kt:87-93` (create-response order) | Yes |
| TRAIN-02 | 1-based number **per team**, `day` ascending, tie by id, `teamId: null` → `number: null` | `TrainingNumberingTest.kt:24-35` (day order → 1,2,3), `:38-51` (equal days, 20 shuffled reruns, tie broken by `id.toString()` ascending), `:54-60` (`assertNull(numbers[unassigned.id])`), `:63-75` (team B restarts at 1 while team A continues) | Yes — all four sub-clauses individually asserted |
| TRAIN-03 | `?teamId=` numbers identical to unfiltered numbers (the T29 regression risk) | `TrainingReadsIT.kt:88-105` — `assert(filtered == unfiltered)` comparing day→number maps built from both calls; `assert(filtered.values.toList() == listOf(1, 2))` | Yes |
| TRAIN-04 | `?assigned=false` returns only `teamId: null` trainings | `TrainingReadsIT.kt:107-120` — `assert(unassigned.size == 1)`, `assert(unassigned[0]["teamId"].isNull)`, `assert(unassigned[0]["number"].isNull)` | Yes |
| TRAIN-05 | `POST /trainings` creates training + exercises in one transaction, `201` | `TrainingControllerIT.kt:63-112` — asserts `201`, `Location` header, 3 exercises in order, and a follow-up `PATCH {}` re-read proves persistence, not an echo | Yes |
| TRAIN-06 | `duration` ≤0 or >480 → `400` | `TrainingControllerIT.kt:179-210` — both create and update paths, `duration` 0 and 481, asserts `400` + field message | Yes |
| TRAIN-07 | `PATCH` with `exercises` replaces wholesale, preserves order; ratings survive an exercise removal (they carry no ratings at all) | `TrainingControllerIT.kt:114-135` (two exercises replace three), `:161-177` (empty array clears all) | Order/replacement: yes. The "ratings belonging to removed exercises" clause has **no direct test** — but the schema (design.md) gives `ratings` no FK to `exercises` at all, so the clause is structurally unrepresentable rather than untested-and-risky. Treated as a minor, non-blocking coverage note, not a failure. |
| TRAIN-08 | `PATCH` omitting `exercises` leaves them untouched | `TrainingControllerIT.kt:137-159` — PATCH with only `duration`, asserts both original exercises still present in order | Yes |
| TRAIN-09 | Diagram `jsonb`; reject >8192 bytes or >60 shapes with `400`; reject unknown `kind` | `DiagramValidatorTest.kt:18-23` (61 shapes rejected), `:26-32` (60 accepted), `:34-40` (>8192 bytes rejected), `:42-47` (unknown kind rejected); HTTP round-trip for shape-count and kind in `TrainingDiagramIT.kt:89-107` (formerly `:77-95`); byte-limit now also HTTP-proven at `TrainingDiagramIT.kt:77-87` (`d0789bf`) | Yes. Byte-limit is now proven both at the unit level and through the real HTTP → jsonb round trip. |
| TRAIN-10 | `diagram: null` stores/returns `null`, never `{}` | `DiagramValidatorTest.kt:70-73` (`assertNull`), `TrainingDiagramIT.kt:54-61` (HTTP: `jsonPath("$.exercises[0].diagram").value(nullValue())`) | Yes |
| TRAIN-11 | `DELETE /trainings/{id}` deletes exercises + that training's ratings, `204`; other trainings' ratings untouched | `TrainingDeleteIT.kt:56-89` — deletes one training with an exercise+rating, asserts both gone, asserts the other training's rating (`ratingForSurvivor`) still present | Yes |
| TRAIN-12 | `teamId` owned by another user → `400 unknown-team`, not `404` | `TrainingControllerIT.kt:212-226` — user B posts with user A's `teamId`, asserts `status().isBadRequest` and `$.type` = `.../problems/unknown-team` | Yes |

**12/12 ACs fully matched with correct spec-defined outcomes; TRAIN-07 has one untested-but-structurally-moot sub-clause (non-blocking, see row above).**

### Edge cases (spec.md "Edge Cases" section)

| Edge case | Evidence | Verdict |
| --- | --- | --- |
| "a training's `day` is an invalid date THEN reject at `400`, rather than replicating the frontend's `Infinity` sort fallback" | `TrainingControllerIT.kt:212-222` (`d0789bf`) — `POST /trainings` with `day: "not-a-date"`, asserts `status().isBadRequest`. Sanity-checked during re-verification: temporarily mutating the test's `day` to a genuinely valid ISO instant (`"2026-08-17T10:00:00Z"`) made the same test fail with `Status expected:<400> but was:<201>`, confirming the assertion is real (exercises the message-converter path) and not a tautology; reverted, working tree confirmed clean against `d0789bf`. | Covered |
| "two trainings share the exact same `day` THEN numbering breaks the tie by id, deterministically across repeated reads" | `TrainingNumberingTest.kt:37-51` proves determinism at the pure-function level across 20 shuffled reruns of the same input. No IT repeats an actual `GET /trainings` call twice against a same-day pair to prove read-path stability end-to-end, but since `day`/`id` are stored columns (not derived per-request) and the algorithm is a pure sort, the pure-level proof is strong indirect evidence. | Covered (pure-logic evidence; no HTTP-level repeat-read test) |
| "a diagram's shape coordinates fall outside 0–1 THEN clamp... rather than rejecting" | `DiagramValidatorTest.kt:50-68`, `TrainingDiagramIT.kt:64-75` | Covered |

---

### Discrimination sensor

All mutations applied to a clean working tree one at a time, `./gradlew test --tests "com.coachplanner.api.training.*"` run after each, then reverted (`git checkout --`) and `git status --short` confirmed empty before the next mutation. Final tree confirmed clean.

| # | Mutation | File | Result | Killed by |
| --- | --- | --- | --- | --- |
| 1 | Tie-break comparator flipped: `.thenBy { it.id.toString() }` → `.thenByDescending { ... }` | `TrainingNumbering.kt:33` | **Killed** | `TrainingNumberingTest > equal days break the tie by id, identically across repeated runs` |
| 2a | `getAll` filters by `teamId`/`assigned` **before** computing numbers (naive move) | `TrainingService.kt:29-38` | **Survived** — see note below | — |
| 2b | `getAll` numbers by **output position** instead of `TrainingNumbering`'s per-team grouping (the actual T29-shaped bug: global/positional numbering instead of per-team) | `TrainingService.kt:29-38` | **Killed** | `TrainingReadsIT > filtering by teamId returns the same number values as the unfiltered call` |
| 3 | Shape-count check `>` → `>=` (off-by-one on the 60-shape limit) | `DiagramValidator.kt:31` | **Killed** | `DiagramValidatorTest > a diagram with exactly 60 shapes is accepted` |
| 4 | `update()` always replaces exercises, even when `exercises` is absent from the request | `TrainingService.kt:68` | **Killed** | `TrainingControllerIT > POST trainings persists the training and all three exercises in order`, `TrainingControllerIT > PATCH without the exercises key leaves the existing exercises intact` |
| 5 | `requireOwnedTeam` no longer verifies ownership — any `teamId` accepted | `TrainingService.kt:117-122` | **Killed** | `TrainingControllerIT > a teamId owned by another user is 400 unknown-team, never 404` |

**Note on mutation 2a:** moving the `teamId`/`assigned` filters before `TrainingNumbering.numbersById(...)` did **not** fail any test, because `TrainingNumbering` groups internally by `team_id`. Filtering to a single team's full history before numbering yields the same per-team-grouped result as numbering first and then filtering — other teams never influence a given team's own sequence in this algorithm. This is not a suite weakness; it means the *specific* "filter-then-group" mistake the algorithm's own structure already forecloses. Mutation 2b reproduces the actual failure mode tasks.md's risk table describes (numbers computed from output position rather than the team-grouped algorithm), and that was caught immediately by the `TrainingReadsIT` filtered/unfiltered-parity test — confirming the AC TRAIN-03 test genuinely guards against the real regression shape, just not against the equivalent-by-construction naive reordering in 2a.

**Sensor summary: 5 targeted mutations (with one refinement), 5 killed on the meaningful variant, 0 survived that represented an actual behavioral risk.**

---

### Gate

Re-verification pass (`d0789bf`): `./gradlew build` (full suite, real Testcontainers-backed PostgreSQL, Docker confirmed running via `docker info`):

```
BUILD SUCCESSFUL in 25s
7 actionable tasks: 2 executed, 5 up-to-date
```

Aggregated JUnit XML across all 38 test-result files: **149 tests, 0 failures, 0 errors** (up from 147 — the two new tests from `d0789bf`). Targeted re-run of the two touched classes (`TrainingControllerIT`, `TrainingDiagramIT`) also independently green.

Prior full-suite gate (`3106008`, first pass): `BUILD SUCCESSFUL in 27s`, 147 tests, 0 failures, 0 errors.

---

### Ranked gaps

Both blocking/minor gaps from the first pass are now closed as of `d0789bf`:

1. ~~Edge case not tested: "a training's `day` is an invalid date THEN reject at `400`"~~ — **Closed.** `TrainingControllerIT.kt:212-222` now exercises `POST /trainings` with `day: "not-a-date"` through the real message-converter path and asserts `400`. Re-verification sanity-checked the assertion is load-bearing (see edge-case table above).
2. ~~Minor: TRAIN-09's 8192-byte limit proven only at unit level~~ — **Closed.** `TrainingDiagramIT.kt:77-87` now posts a >8192-byte diagram through the real HTTP → `jsonb` round trip and asserts `400`, matching how shape-count and kind were already covered.
3. **Still open, non-blocking** (unchanged from first pass, not in scope of this fix): TRAIN-07's clause "ratings belonging to removed exercises... only if the training itself is deleted" has no direct test on the update path (only on the delete path, `TrainingDeleteIT.kt`). Non-blocking: the schema gives `ratings` no FK to `exercises`, so the scenario the clause warns against cannot occur regardless of service code.

---

## Phase 6: Games & standings (T32–T37)

**Verdict: PASS ✅**

**Commit range covered:** `d0789bf..ebe26df` (`e58498d`, `9b91674`, `e7322b9`, `39a082d`, `310a70a`, `bd96342`, `b74cf71`, `ebe26df`)

Note: `ebe26df` (after this report was written) tightened the negative-figure-on-update test's request body so it isolates `@Valid`'s bean-validation path from `requireConsistent`'s sum check, per this report's own non-blocking precision note below — no behavior change, test-only.

**Verifier:** fresh sub-agent, author ≠ verifier, evidence-or-zero methodology. This is a re-verification pass over fix commit `b74cf71`, which closed the one blocking defect and four coverage gaps from the first pass (see history below the ranked-gaps table).

Re-checked all five items from the first pass directly against the fix commit: `@Valid` presence in source, each new/changed test's actual assertions, and a real `./gradlew test` run — plus a hand experiment (temporarily stripping `@Valid` in the working tree, confirming the specific defect symptom, then restoring it and confirming `git status --short` was clean). The full gate (`./gradlew build`) passed clean at 181 tests.

---

### Spec-anchored AC coverage

| AC | Requirement (paraphrased) | Evidence (file:line) | Assertion matches spec outcome? |
| --- | --- | --- | --- |
| GAME-01 (AC1) | `GET /games` returns caller's games, date descending | `GameReadsIT.kt:120-131` — creates 3 games on 3 dates, asserts `games.map { date }` equals the strict date-descending list | Yes |
| GAME-02 (AC2) | `?teamId=`, `?status=`, `?assigned=false` filters; a recorded `0-0` classifies as `played` (null-vs-zero trap) | `GameReadsIT.kt:77-92` — 0-0 seeded via repository, asserts present under `?status=played`, absent under `?status=scheduled` (the "single most important assertion" per tasks.md); `:94-104` (no-result game → scheduled, not played); `:106-117` (`?assigned=false` → only null-team games); `:107-121` (new) — `teamId filters to only that team's games`: creates two teams + a game on each + an unassigned game, filters by `teamId=$teamA`, asserts exactly one result and that it's `gameForA` | **Yes, now fully matches.** All three filter sub-clauses covered. |
| GAME-03 (AC3) | `POST /games` forces `usScore`/`themScore` null, ignores any supplied | `GameControllerIT.kt:53-74` — posts with `"usScore":3,"themScore":1"` in the body, asserts `201`, `Location` header, and `jsonPath("$.usScore")`/`$.themScore` both `nullValue()` | Yes |
| GAME-04 (AC4) | `PATCH /games/{id}` rejects `usScore`/`themScore` with `400` | `GameControllerIT.kt:76-100` — two separate tests, one per field, both assert `status().isBadRequest`; `:102-115` confirms a score-free PATCH still succeeds normally (`200` + `opponent` updated) | Yes |
| GAME-05 (AC5) | `PUT /games/{id}/result` with two integers 0-99 stores and returns them | `GameResultIT.kt:53-67` — `0-0` recorded, `jsonPath("$.usScore").value(0)`, `$.themScore` `.value(0)` | Yes |
| GAME-06 (AC6) | `DELETE /games/{id}/result` nulls both scores, fixture survives | `GameResultIT.kt:69-85` — records `3-1`, then clears, asserts both fields `nullValue()` and `$.id` unchanged (same fixture, not a new one) | Yes |
| GAME-07 (AC7) | `DELETE /games/{id}` cascades cards + ratings for that game only | `GameDeleteIT.kt:59-88` — creates a card+rating on the deleted game and on a surviving game, asserts the deleted game's card/rating are gone (`findById(...).isEmpty`) and the surviving game's are untouched (`.isPresent`); also `OwnershipIsolationIT.kt` (new block, ~line 196) extends 404-not-403 isolation to `GET`/`PATCH`/`DELETE /games/{id}` | Yes |
| STAND-01 (AC8) | `GET /standings/rivals` returns the caller's rival rows | `RivalRowControllerIT.kt:86-97` (new) — `GET standings rivals returns the caller's created rows`: creates one rival row, `GET`s the collection, asserts `$.length()` is 1 and the row's `name`/`played` come back correctly | Yes |
| STAND-02 (AC9) | `won+drawn+lost≠played` or any negative figure → `400` naming the mismatch | Sum-mismatch: `RivalRowControllerIT.kt:49-56` (create), `:85-100`→now `:130-145` (update) both assert `400` + exact `$.detail` message. Negative figure: `RivalRowControllerIT.kt:58-65` (create) asserts `400` + `$.errors.won`; `:99-114` (new) — `a negative figure on update is rejected with 400, not 500` — asserts `400` + `$.errors.won == "must not be negative"` on `PATCH`. `StandingsController.kt:49` now carries `@Valid` on `updateRival`. | **Yes, update path now correct** — see "Correctness defect, closed" below for the fix mechanics and a precision note on the new test's exact scenario. |
| STAND-03 (AC10) | `points`/`goalDifference` in the body are ignored, never stored | `RivalRowControllerIT.kt:76-83` — posts with `"points":99"`, asserts `201` and `jsonPath("$.points").doesNotExist()` (the DTO shape itself has no such field, so "ignored" is structural, not just behavioral) | Yes |
| STAND-04 (AC11) | `GET /standings?teamId=` returns full sorted table: own row + rival rows, points→GD→goals-for→name, each level a genuine tiebreak | `StandingsCalculatorTest.kt:74-96` — 7 rows engineered so **each of the four levels is the actual deciding factor** for at least one pair (top-on-points; two rows tied on points separated only by GD; two tied on points+GD separated only by goals-for; two tied on all three separated only by name), asserts the exact sorted name order. HTTP-level: `StandingsIT.kt:78-102` exercises 2 of the 4 levels (points tie broken by GD) through the real endpoint, own row + one rival row combined | Yes — the pure-logic test is a genuine 4-level discriminating fixture, not a coincidentally-already-sorted list; HTTP level covers the mechanism partially but sufficiently, given the unit test's rigor |
| STAND-04 (AC12) | A team with no played games still gets an all-zero row, present, never absent/null | `StandingsCalculatorTest.kt:36-49` (pure) and `StandingsIT.kt:49-61` (HTTP: `$.length()` = 1, `played`/`points` both `0`, `isOurs: true`) | Yes |

**12/12 ACs fully matched** as of `b74cf71`. (First pass: 9/12 — AC2's `?teamId=` sub-clause untested, AC8 had zero coverage, AC9's update path silently 500'd. All three closed by this commit.)

### Correctness defect, closed — re-verified by hand, not just by reading the diff

Re-confirmed the original defect mechanics: `StandingsController.updateRival` previously took `@RequestBody request: UpdateRivalRowRequest` with no `@Valid`, so `UpdateRivalRowRequest`'s `@field:Min(0, message = "must not be negative")` annotations (`UpdateRivalRowRequest.kt:9-25`) never ran, and `RivalRowService.update` (`RivalRowService.kt:36-55`) only re-validates the won+drawn+lost sum, never individual field signs — the DB's own `CHECK` constraint was the only backstop, surfacing as an unmapped `DataIntegrityViolationException` → generic `500` via `ApiExceptionHandler`'s catch-all.

**Fix confirmed present:** `StandingsController.kt:49` — `@Valid @RequestBody request: UpdateRivalRowRequest`.

**Verified directly, not inferred**, with a scratch experiment in the working tree (not part of the diff):
1. Temporarily removed `@Valid` from `updateRival`, ran `RivalRowControllerIT`. The new `a negative figure on update is rejected with 400, not 500` test **failed** as expected — but on `java.lang.AssertionError: No value at JSON path "$.errors.won"`, not on the status code. Reason: that test's exact input (`played` unchanged at whatever it was created with, `{"won":-1}` alone patched) still fails the *sum*-consistency check in `RivalRowService.requireConsistent` regardless of `@Valid`, so the service's own `ValidationException` already returns `400` — just without an `errors` object, since that path doesn't go through `MethodArgumentNotValidException`. **Precision note:** this specific committed test therefore is a valid regression sensor for "the fix is present" (it does fail without `@Valid`), but it does not reproduce the literal `500` symptom the bug report and commit message describe — it never reaches the DB-CHECK/500 path at all, because the sum check catches it first.
2. To directly re-verify the original `500` claim, ran an ad-hoc scratch test (`ScratchNegativeIT.kt`, created, run, then deleted — never committed) with a genuinely sum-consistent negative input (`played=1`, patched to `{"won":-1,"drawn":2,"lost":0}`, sum still `1`). With `@Valid` removed: **`500`**, body `{"status":500,"title":"Internal Server Error","type":".../internal-error"}` — reproducing the original bug exactly. With `@Valid` restored: **`400`**, body includes `"errors":{"won":"must not be negative"}` — confirming the fix closes the real gap.
3. Restored `@Valid`, deleted the scratch test file, confirmed `git status --short` was empty (clean) before proceeding.

Net: the fix is correct and closes the real defect (AC STAND-02/AC9's update-path requirement is now met for the true failure mode). The committed regression test is *sufficient as a sensor* (mutation-kills on `@Valid` removal) but is *not spec-precise* — its own name ("not 500") and the commit message both imply it reproduces the sum-consistent negative-figure scenario from the original report, and it does not; it happens to also be caught by the pre-existing sum check. Flagged as a non-blocking follow-up below, not a re-open of the blocking verdict, since the underlying behavior (400 on any negative figure, sum-consistent or not) is genuinely fixed and verified above by hand.

---

### Discrimination sensor

All mutations applied to a clean working tree one at a time, targeted test classes run after each (`com.coachplanner.api.game.*` and/or `com.coachplanner.api.standings.*`), then reverted with `git checkout --` and `git status --short` confirmed empty before the next mutation. Docker confirmed running (`docker info`) throughout, so all Testcontainers-backed ITs ran for real.

| # | Mutation | File | Result | Killed by |
| --- | --- | --- | --- | --- |
| 1 | `hasResult` polarity flipped: `usScore != null && themScore != null` → `usScore == null || themScore == null` | `GameResult.kt:10` | **Killed** | `GameReadsIT > a recorded 0-0 game is classified as played...`, `GameReadsIT > a game with no result appears under scheduled...`, `StandingsCalculatorTest > only played games count...`, `StandingsIT > recording a 0-0 draw contributes...`, `StandingsIT > a scheduled fixture with no result contributes nothing...` (5 tests) |
| 2 | Status filter swapped: `"played" -> hasResult(it)` / `"scheduled" -> !hasResult(it)` reversed | `GameService.kt:24-25` | **Killed** | `GameReadsIT > a game with no result appears under scheduled, not played`, `GameReadsIT > a recorded 0-0 game is classified as played...` |
| 3 | `sortedByDescending { it.date }` → `sortedBy { it.date }` | `GameService.kt:29` | **Killed** | `GameReadsIT > GET games returns the caller's games ordered by date descending` |
| 4 | `create()` bound `usScore`/`themScore` from the request instead of leaving them unset (required adding the fields to `CreateGameRequest` too, to simulate a caller-supplied-score bug) | `GameService.kt`, `CreateGameRequest.kt` | **Killed** | `GameControllerIT > POST games with usScore in the body is created with null scores, ignoring it` |
| 5 | Removed the `usScore != null \|\| themScore != null` rejection block from `update()` entirely | `GameService.kt:52-57` | **Killed** | `GameControllerIT > PATCH games with usScore is rejected with 400`, `GameControllerIT > PATCH games with themScore is rejected with 400` |
| 6 | Removed the `requireConsistent` re-check from the **update** path only (create path left protected) | `RivalRowService.kt:51-52` | **Killed** | `RivalRowControllerIT > updating a row so won plus drawn plus lost no longer equals played is rejected with 400` (fails because the response comes back `500` instead of the expected `400` — same underlying gap as the correctness defect above, just from a different angle) |
| 7 | Dropped the goals-for tiebreak level: `.thenByDescending { it.goalsFor }` removed from `sort()` | `StandingsCalculator.kt:44-49` | **Killed** | `StandingsCalculatorTest > sort breaks ties by points, then goal difference, then goals for, then name` |
| 8 | `computeOurRow` stopped filtering by `hasResult`, counting all games as played | `StandingsCalculator.kt:16` | **Killed** | `StandingsCalculatorTest > only played games count — a scheduled fixture contributes nothing`, `StandingsIT > a scheduled fixture with no result contributes nothing to the team's own row` |

**Sensor summary: 8 mutations injected, 8 killed, 0 survived.** Working tree confirmed clean (`git status --short` empty, `git diff --stat d0789bf..HEAD` unchanged) after the full sequence.

---

### Gate

`./gradlew build` (full suite, real Testcontainers-backed PostgreSQL, Docker confirmed running via `docker info`):

```
BUILD SUCCESSFUL in 31s
7 actionable tasks: 2 executed, 5 up-to-date
```

Aggregated JUnit XML across all test-result files: **181 tests, 0 failures, 0 errors** — matches the count called out in the `b74cf71` commit (+4 over the prior pass's 177, one per closed coverage gap).

Also ran the Phase 6 test classes directly (`./gradlew test --tests "com.coachplanner.api.game.*" --tests "com.coachplanner.api.standings.*"`) — green.

---

### Ranked gaps — status as of `b74cf71`

1. ~~**Correctness defect, blocking**: `PATCH /api/v1/standings/rivals/{id}` with a negative figure that still sums consistently returns `500`, not `400`.~~ — **Closed.** `StandingsController.kt:49` now carries `@Valid` on `updateRival`. Re-verified by hand: with `@Valid` stripped in a scratch edit, a genuinely sum-consistent negative-figure request (`played=1`, `{"won":-1,"drawn":2,"lost":0}`) reproduces the exact original `500`/`internal-error` body; with `@Valid` restored, the same request returns `400` with `errors.won: "must not be negative"`. Working tree confirmed clean (`git status --short`) after the experiment. See "Correctness defect, closed" above for the full trace, including a precision note on the committed test's own scenario.
2. ~~**Coverage gap**: `GET /api/v1/standings/rivals` (AC STAND-01 / AC8) had zero test coverage.~~ — **Closed.** `RivalRowControllerIT.kt:86-97`, `GET standings rivals returns the caller's created rows`.
3. ~~**Coverage gap**: the `?teamId={id}` filter half of AC GAME-02 (AC2) was untested.~~ — **Closed.** `GameReadsIT.kt:107-121`, `teamId filters to only that team's games`.
4. ~~**Minor, non-blocking**: the spec's Independent Test for P6 was proven only as two separate halves, never one continuous chain.~~ — **Closed.** `StandingsIT.kt:71-107` (rewritten), `recording a 0-0 through the API makes the game appear as played and contributes a draw and a point` — a single test that `POST`s a game, `PUT`s a `0-0` result via `/games/{id}/result`, confirms it under `GET /games?status=played`, then confirms the `GET /standings?teamId=` row reflects it. Genuine end-to-end HTTP chain, not repository-seeded.
5. ~~**Minor, non-blocking**: `GET /games/{id}`'s happy-path body was never asserted.~~ — **Closed.** `GameControllerIT.kt:78-92`, `GET games by id returns the game's full body` — asserts `id`, `opponent`, `competition`, `date`, `isHome`, and both scores `null`.
6. **New, non-blocking, spec-precision only**: the committed test `RivalRowControllerIT.kt:99-114` (`a negative figure on update is rejected with 400, not 500`) uses `{"won":-1}` alone, which — independent of `@Valid` — already fails `RivalRowService.requireConsistent`'s sum check and returns `400` via a different code path (no `@Valid` needed to get *a* `400` for this exact input; `@Valid` is needed to get the `errors.won` field the test also asserts). It is a valid, sufficient regression sensor for "the fix is present" (fails without `@Valid`, on the `errors.won` assertion), but its name and the commit message both suggest it reproduces the original sum-consistent-negative/500 scenario, and by design it does not exercise that exact branch. Not blocking — the underlying behavior is independently verified correct by hand above — but worth tightening to a sum-consistent input (e.g. `played` unchanged, `{"won":-1,"drawn":+1}` keeping the sum equal) so the test's own scenario matches its docstring/name.
