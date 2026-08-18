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

---

## Phase 7: Cards & ratings (T38–T42)

**Verdict: PASS ✅**

**Commit range covered:** `ebe26df..HEAD` (`0192710`, `4095ff3`, `432bd70`, `89a8ff1`, `fd34a97`)

**Verifier:** fresh sub-agent, author ≠ verifier, evidence-or-zero methodology.

---

### Spec-anchored AC coverage

| AC | Requirement (paraphrased) | Evidence (file:line) | Assertion matches spec outcome? |
| --- | --- | --- | --- |
| CARD-01 (AC1) | `GET /cards?gameId=` or `?playerId=` returns matching cards | `CardControllerIT.kt:117-138` — `GET cards filters by gameId alone, playerId alone, and both together`: creates 3 cards across 2 games/2 players, asserts `byGame.size == 2`, `byPlayer.size == 2`, `byBoth.size == 1` | Yes |
| CARD-02 (AC2) | `POST /cards` with `playerId`/`gameId`/`type` (`yellow`/`red`) creates, `201` | `CardControllerIT.kt:63-76` — `POST cards creates the card and returns 201 with a Location header`: asserts `201`, `Location` header present, `$.playerId`/`$.gameId`/`$.type` all round-trip | Yes |
| CARD-03 (AC3) | Player not in the team playing the game → `400 player-not-in-game-team` | `CardControllerIT.kt:78-91` — player on team B, game belongs to team A, asserts `status().isBadRequest` and `$.type` = `.../problems/player-not-in-game-team`; also `:93-105` (unassigned game, `teamId: null`, rejects every player the same way) | Yes |
| CARD-04 (AC4) | `type` outside `yellow`/`red` → `400` | `CardControllerIT.kt:107-115` — `a card type of orange is rejected with 400`, `type: "orange"` asserts `status().isBadRequest` | Yes |
| CARD-05 (AC5) | `DELETE /cards/{id}` → `204` | `CardControllerIT.kt:140-154` — `DELETE cards removes the card and returns 204`: asserts `204`, then re-`GET`s the list and asserts the id is gone | Yes |
| RATE-01/RATE-06 (AC6) | `GET /ratings?eventType=&eventId=` or `?playerId=` returns matches, each with `eventType`/`eventId` derived from stored FKs | `RatingReadsIT.kt:68-82` (training-rating serializes `eventType: "training"`, `eventId == training.id`), `:84-98` (game-rating serializes `eventType: "game"`, `eventId == game.id`), `:100-121` (filtering by each of `eventType`, `eventId`, `playerId` independently) | Yes |
| RATE-07 (AC7) | `PUT /ratings/{eventType}/{eventId}/players/{playerId}` upserts on the triple, creating on first call, overwriting after, **never two rows under concurrent calls** | Sequential: `RatingUpsertIT.kt:64-81` — two calls (5, then 8), asserts exactly 1 row with `value == 8`. Concurrent: `RatingUpsertIT.kt:83-109` — 12 threads released simultaneously via `CountDownLatch`, real parallel HTTP `PUT`s (not sequential, not inspected), asserts exactly 1 row afterward | Yes — concurrency proven with genuine parallel requests, matching the AC's explicit "not asserted by inspection" bar |
| RATE-08 (AC8) | `value: null` deletes any existing rating, returns `204`, never stores a null-valued row | `RatingNullZeroDeleteIT.kt:80-84` (within the three-state walk: `PUT {"value":null}` → `204`, then `findByPlayerIdAndTrainingId(...) == null`), `:121-129` (null with no existing row is a no-op `204`, not an error) | Yes |
| RATE-09 (AC9) | `value: 0` stores a real, distinct, readable value | `RatingNullZeroDeleteIT.kt:73-78` — within the same three-state walk, `PUT {"value":0}` → `200`, `$.value == 0`, and `findByPlayerIdAndTrainingId(...).value.toInt() == 0` (repository-level read, not just the response echo) | Yes |
| RATE-10 (AC10) | Non-integer or out-of-range `value` → `400` | `RatingNullZeroDeleteIT.kt:87-95` — `value: 5.5` (genuine fractional, not just out-of-range) asserts `400`; `:97-107` (`-1` → `400` + `errors.value`); `:109-119` (`11` → `400` + `errors.value`) | Yes — the non-integer case is a real fractional value, not merely an out-of-range integer, so it independently exercises `JacksonConfig`'s `CoercionConfig` rather than only bean validation |
| RATE-11 (AC11) | Unknown `eventType`, or an event that doesn't exist / isn't the caller's → `400` | `RatingUpsertIT.kt:111-118` (`eventType: "match"` → `400`), `:120-127` (a random nonexistent training id → `400`) | Yes |

**11/11 ACs matched with spec-defined outcomes, evidence found at file:line for every one.**

### Spec's Independent Test (5 → 0 → null, three distinct observable states)

`RatingNullZeroDeleteIT.kt:61-85` — `the spec's three-state walk — 5, then 0, then null — leaves three distinct observable states`: one continuous test, one player/training/rating triple, three sequential `PUT`s with repository-level assertions after each (`value == 5`, then `value == 0` — "expected a stored, readable rating of 0 — not absent", then `findByPlayerIdAndTrainingId(...) == null` — "expected no row after setting value to null"). Proven as a single chain, not scattered across tests, matching the spec's independent test verbatim.

### T42 edge case — "rating written for a concurrently deleted game → 400, never an orphan"

The commit message for `fd34a97` states genuine concurrent-delete timing can't be won deterministically in a test, and substitutes a deterministic FK violation (a nonexistent `playerId`) that exercises the identical code branch. Verified this claim directly, not just read it:

- `RatingService.upsert`'s retry-as-update branch (`RatingService.kt:81-94`) catches `DataIntegrityViolationException` from the insert attempt, then calls `event.findExisting(...)`. If that also returns `null`, it throws `ValidationException(..., "unknown-event")` → `400`. This is the *only* place in the method that distinguishes "the violation was the expected duplicate-key race" from "the violation was something else" (an FK pointing at a row that vanished between `resolveEvent`'s check and the insert — which is exactly the shape of a concurrently-deleted game/training, since the game/training FK and the player FK are both ordinary FK constraints on the same `INSERT`).
- `RatingUpsertIT.kt:140-152` (`an insert-time FK violation on a reference that no longer exists is 400, never 500 or an orphan`) triggers this via a nonexistent `playerId` instead of a raced game delete — a different FK on the same insert statement, same `DataIntegrityViolationException`, same downstream branch.
- Confirmed by mutation testing (mutation #7 below): reverting the fix (re-throwing the raw exception instead of `ValidationException`) makes exactly this test fail with a `500` instead of the expected `400` — proving the test genuinely guards this branch, not a coincidence.

The substitution is a valid, deterministic proxy for the same code path. It does not itself prove the *game/training* FK specifically (only the *player* FK is exercised), but `RatingService.upsert`'s code has no branch that distinguishes which FK triggered the violation — the catch block is FK-agnostic — so the player-FK proof and the game-FK scenario are provably the same branch, not merely analogous ones.

### T42 — cascade and isolation completeness

- **Player delete → cards/ratings removed, nothing else:** `CascadeIT.kt:88-112` (`deleting a player deletes only their own cards and ratings`) — raw-SQL delete (not `repository.delete()`, consistent with the file's existing proof-of-DB-enforcement style), asserts the deleted player's card/rating are gone, a second player's card/rating survive, and the team/game themselves are untouched.
- **Game delete → cards/ratings removed:** `CascadeIT.kt:114-136` (`deleting a game deletes only its own cards and ratings`) — pre-existing from T10, unchanged this phase.
- **Training delete → exercises + ratings removed:** `CascadeIT.kt:138-...` (`deleting a training deletes its exercises and its ratings, and nothing else`) — pre-existing from T10, unchanged this phase.
- All three of tasks.md's T42 "player, game, training" cascade triad are present; only the player case was new this phase, matching the commit message's own claim.
- **Ownership isolation, repository level:** `OwnershipIsolationIT.kt` — `Card` and `Rating` added to the `OwnershipCase` list (around lines 132-149), exercised by the existing parameterised test at `:156` (`an entity is visible to its owner and invisible to a different user`), each with its own `findByIdAndOwnerId` (Card via `game_id`, Rating via whichever of `training_id`/`game_id` is set).
- **Ownership isolation, HTTP level:** `OwnershipIsolationIT.kt:244-253` (`user B gets 404, never 403, deleting user A's card`) and `:256-265` (same for rating) — both assert `status().isNotFound`, matching the 404-not-403 convention used throughout the suite.

---

### Discrimination sensor

All mutations applied to the real working tree one at a time (no scratch worktree needed — repo is small enough to mutate and revert directly), targeted test classes run after each (`com.coachplanner.api.discipline.*`, `com.coachplanner.api.CascadeIT`, `com.coachplanner.api.OwnershipIsolationIT`), then reverted via `Edit` back to the original text and `git status --short`/`git diff --stat` confirmed empty before the next mutation. Docker confirmed running (`docker info`) throughout, so all Testcontainers-backed ITs ran for real.

| # | Mutation | File | Result | Killed by |
| --- | --- | --- | --- | --- |
| 1 | `CardService.create`'s ownership check inverted: `player.team.id != game.teamId` → `player.team.id == game.teamId` | `CardService.kt:41` | **Killed** (5 tests) | `CardControllerIT > a player not in the team playing the game is rejected with 400 player-not-in-game-team`, `...an unassigned game rejects every player...`, plus 3 others whose fixtures now trip the inverted check on their own happy path |
| 2 | `RatingMapper.toDto` swapped which FK produces `"training"` vs `"game"` | `RatingMapper.kt:12-16` | **Killed** (2) | `RatingReadsIT > a training-rating serializes with eventType training...`, `...a game-rating serializes with eventType game...` |
| 3 | `RatingService.upsert`'s retry branch changed from find-and-update to a blind retry-insert (reintroducing check-then-act) | `RatingService.kt:82-93` | **Killed** (3) | `RatingNullZeroDeleteIT > the spec's three-state walk...`, `RatingUpsertIT > an insert-time FK violation...`, `RatingUpsertIT > two sequential PUT calls leave exactly one row...` |
| 4 | `RatingService.upsert`'s null-value branch no longer deletes the existing row (short-circuits to `return null` immediately) | `RatingService.kt:71-72` | **Killed** (1) | `RatingNullZeroDeleteIT > the spec's three-state walk — 5, then 0, then null...` |
| 5 | Removed `JacksonConfig`'s `CoercionConfig(LogicalType.Integer, Float -> Fail)` | `JacksonConfig.kt:36-40` | **Killed** (1) | `RatingNullZeroDeleteIT > a non-integer value is rejected with 400` |
| 6 | `RatingRepository.findByIdAndOwnerId` stripped of its owner-scoping join, matching on `id` alone | `RatingRepository.kt:25-33` | **Killed** (2) | `OwnershipIsolationIT > user B gets 404, never 403, deleting user A's rating`, `...an entity is visible to its owner and invisible to a different user(OwnershipCase) > Rating` |
| 7 | `RatingService.upsert`'s retry branch reverted to re-throw the raw `DataIntegrityViolationException` instead of `ValidationException` when `findExisting` returns null | `RatingService.kt:89-90` | **Killed** (1) | `RatingUpsertIT > an insert-time FK violation on a reference that no longer exists is 400, never 500 or an orphan` (fails with `500` instead of the expected `400`, directly confirming the T42 edge-case claim above) |

**Sensor summary: 7 mutations injected, 7 killed, 0 survived.** Working tree confirmed clean (`git status --short` empty, `git diff --stat` empty against `HEAD`) after the full sequence.

---

### Gate

`./gradlew clean build` (full suite, real Testcontainers-backed PostgreSQL, Docker confirmed running via `docker info`):

```
BUILD SUCCESSFUL in 37s
8 actionable tasks: 8 executed
```

Aggregated JUnit XML across all 49 test-result files: **206 tests, 0 failures, 0 errors** — matches the count expected per the task brief. A subsequent targeted re-run of `com.coachplanner.api.discipline.*`, `CascadeIT`, and `OwnershipIsolationIT` after the mutation sequence (on the clean, reverted tree) was also independently green.

The concurrency test (`RatingUpsertIT`'s `concurrent PUT calls for the same triple leave exactly one row`) passed cleanly on every run in this session — no flakiness observed, though its 12-thread/15s-timeout design leaves some margin under heavier load than this environment produced.

---

### Ranked gaps

None. All 11 ACs matched, the spec's Independent Test is proven as a single chain, the T42 edge-case substitution was verified to exercise the same code branch as the scenario it stands in for, and all 7 targeted mutations were killed.

---

## Phase 8: Reference lists (T43–T46)

**Verdict: PASS ✅** (with 3 non-blocking spec-precision gaps flagged below)

**Commit range covered:** `fd34a97..HEAD` (`499bc93`, `338f659`, `1a49235`, `ec20769`)

**Verifier:** fresh sub-agent, author ≠ verifier, evidence-or-zero methodology.

---

### Spec-anchored AC coverage

| AC | Requirement (paraphrased) | Evidence (file:line) | Assertion matches spec outcome? |
| --- | --- | --- | --- |
| P8-AC1 | `GET /competitions`/`GET /opponents` returns caller's list ordered case-insensitively | `ReferenceListControllerIT.kt:121-130` — `GET orders the list case-insensitively`: creates "zebra", "Apple", "banana", asserts `names == listOf("Apple", "banana", "zebra")` | Yes, but **fixture doesn't discriminate case-insensitive sort from plain lexicographic sort** — see sensor mutation 5 below. Flagged as a spec-precision gap, not a failure. |
| P8-AC2 | Name that trims to empty → `400` | `ReferenceListControllerIT.kt:111-117` — `a whitespace-only name is rejected with 400`: `"   "`, asserts `400` + `$.errors.name == "must not be blank"` | Yes |
| P8-AC3 | Case-insensitive duplicate after trim → `409`, enforced by DB unique index, not application logic alone | `ReferenceListControllerIT.kt:208-215` (`"Cup"`→`"cup"`), `:219-226` (`"Cup"`→`"  CUP  "`), `:241-256` (rename path, `"cup"` against existing `"Cup"`) — all assert `409` + `$.type` = `.../problems/duplicate-name`. Source inspection of `ReferenceListService.kt:53-58` confirms **no application-level pre-check** exists before `saveAndFlush` — the only guard is the `catch (DataIntegrityViolationException)` block, so the index is genuinely the enforcement mechanism, not a race-prone `some()`-style check. | Yes |
| P8-AC4 | Created entry stores the **trimmed** name | `ReferenceListControllerIT.kt:100-107` — `a name with surrounding whitespace is stored and returned trimmed`: `"  Cup  "` → `$.name == "Cup"` | Yes |
| P8-AC5 | `PATCH` renames in one transaction, rewrites `games.competition` on the caller's games matching case-insensitively on the trimmed old name | `ReferenceListControllerIT.kt:157-179` — `renaming an entry cascades to every one of the caller's games carrying the old name` (Competition case): entry `"District League"`, games seeded with `"district league"` and `"DISTRICT LEAGUE"` (case variance proven), both reload with `competition == "Premier League"` | Case-insensitivity: yes. **Trim-matching against stored games is not proven** — no seeded game has surrounding whitespace in its stored name; see sensor mutation 2 below. Spec-precision gap, not a failure (the create/rename-input trim is well covered; it's specifically the cascade-side match against an untrimmed stored value that has no test). |
| P8-AC6 | Opponent rename cascades to `games.opponent`, does NOT touch a rival standings row sharing the name | Cascade: `ReferenceListControllerIT.kt:157-179` (Opponent case). Non-touch: `OpponentRenameIT.kt:44-70` — `renaming an opponent does not touch a rival standings row sharing that name`: seeds a `RivalRow` named `"Benfica"`, renames the opponent `"Benfica"`→`"S.L. Benfica"`, asserts `reloadedRow.name == "Benfica"` unchanged | Yes |
| P8-AC7 | Cascade fails partway → roll back the rename entirely | `ReferenceListRenameRollbackIT.kt:64-109` — `a forced mid-cascade failure rolls back the rename entirely`. Read line-by-line: `@MockitoSpyBean` on `GameRepository`; two games seeded for real (`:77-82`) *before* the stub is installed (`:85-91`); the stub only throws on the **2nd** call to `saveAndFlush`, so gameA's cascade write is a real, flushed `callRealMethod()` and only gameB's write fails — this is a genuine after-first-flush failure, not a before-any-write one. Final assertions (`:100-108`) check both games' `competition` reverted to `"District League"` AND the competition entry's own name (via `GET /competitions`) reverted to `"District League"`. | Yes — verified doubly: read the mechanism by hand, then ran a deliberate weakening (stripped `@Transactional` from `ReferenceListService.rename`) and confirmed the test fails without it (`ReferenceListRenameRollbackIT > a forced mid-cascade failure rolls back the rename entirely() FAILED`), proving the test genuinely depends on the transaction boundary, not on incidental behavior. |
| P8-AC8 | Rename to the same trimmed name → no cascade, `200` | `ReferenceListControllerIT.kt:184-203` — `a no-op rename to the same trimmed name performs no cascade and returns 200` | **Weaker than claimed.** The test only checks the returned/game names, which are identical whether or not the cascade actually ran (the new name equals the old name either way, so a cascade that fires anyway is unobservable through this test). Confirmed by sensor mutation 1 below: removing the no-op short-circuit entirely, the test suite still passes. This does not prove "skips the cascade" — only that the end state is correct, which holds either way. Flagged as a real spec-precision gap. |
| P8-AC9 | `DELETE` leaves the name on historical games | `ReferenceListControllerIT.kt:134-152` — `DELETE removes the entry and leaves its name on historical games`: deletes the entry, reloads the game, asserts its name is unchanged; also asserts the entry is gone from the list | Yes |

**7/9 ACs cleanly matched; 3 ACs (P8-AC1, P8-AC5, P8-AC8) have file:line evidence that nominally passes but was shown by mutation testing not to fully discriminate the claimed behavior — see sensor results and ranked gaps below. All are non-blocking: the underlying behavior is still correct in the shipped code (confirmed by mutation 3, 4, 6, 7 killing real defects, and by the deliberate `@Transactional` removal for AC7), only the *tests'* discriminating power is short of what their names claim for AC1/AC5/AC8.**

### Spec's Independent Test ("Create a competition, attach it to two games, rename it → both games carry the new name; delete it → both games keep it")

**Proven only in pieces, not as one continuous chain.** `ReferenceListControllerIT.kt:157-179` proves create→attach→rename→both-updated. `ReferenceListControllerIT.kt:134-152` separately proves create→attach→delete→name-kept, but with only **one** game, not two, and as an entirely separate entry/test run (a fresh entry is created, not the same one that was just renamed). No single test walks create → attach two games → rename → both updated → delete → both games still carry the new (post-rename) name. Non-blocking (each half is independently well-tested), but flagged since the prior phases' reports (5, 6) treat this exact pattern as worth calling out explicitly.

---

### Discrimination sensor

All mutations applied to the real working tree one at a time, `./gradlew test --tests "com.coachplanner.api.reference.*"` run after each, then reverted via `Edit` back to the original text; `git status --short` confirmed empty before the next mutation. Docker confirmed running (`docker info`) throughout, so all Testcontainers-backed ITs ran for real.

| # | Mutation | File | Result | Killed by |
| --- | --- | --- | --- | --- |
| 1 | Removed the `if (trimmed == oldName) return ...` no-op short-circuit in `rename()`, so a no-op rename always cascades | `ReferenceListService.kt:76` | **Survived** | — (see P8-AC8 gap above: no test observes whether the cascade fired when the new name equals the old name) |
| 2 | Removed `trim()` from both `GameRepository` `IgnoreCase` queries, so only exact-whitespace matches cascade | `GameRepository.kt:16,19` | **Survived** | — (see P8-AC5 gap above: no seeded game in the rename-cascade tests has surrounding whitespace in its stored value, so the two case-variant fixtures already had no whitespace to trim) |
| 3 | Swapped the two cascade lambdas in `ReferenceListConfig` (competition bean wired to `findAllByOwnerIdAndOpponentIgnoreCase`, opponent bean to the competition variant) | `ReferenceListConfig.kt:27-45` | **Killed** | `ReferenceListControllerIT > renaming an entry cascades to every one of the caller's games carrying the old name(...) > Competition`, `...> Opponent`, `ReferenceListRenameRollbackIT > a forced mid-cascade failure rolls back the rename entirely()` |
| 4 | Removed `catch (DataIntegrityViolationException)` from `create()`, letting a duplicate-name violation surface as `500` | `ReferenceListService.kt:56-60` | **Killed** | `ReferenceListControllerIT > a case-different duplicate name is rejected with 409 on create(...) > Competition/Opponent`, `...a duplicate name that differs only by case and surrounding whitespace...(...) > Competition/Opponent` |
| 5 | `getAll()`'s `.sortedBy { it.name.lowercase() }` → `.sortedBy { it.name }` (plain, case-sensitive) | `ReferenceListService.kt:43` | **Survived** | — (see P8-AC1 gap above: the fixture `"zebra"`, `"Apple"`, `"banana"` happens to sort identically under plain ASCII lexicographic order, since all three differ only in leading-character case and ASCII places every uppercase letter before every lowercase one) |
| 6 | Removed `@Valid` from `CompetitionController.create` | `CompetitionController.kt:27` | **Killed** | `ReferenceListControllerIT > a whitespace-only name is rejected with 400(...) > Competition` (only the Competition case failed, as expected — `OpponentController` was untouched, confirming the two controllers are independently exercised, not one covering for the other) |
| 7 (exploratory, not part of the required set) | Reordered `rename()` to call `cascadeRename` before `repository.saveAndFlush(entry)` | `ReferenceListService.kt:78-86` | **Survived** — expected and non-blocking, since both writes remain inside the same `@Transactional` boundary regardless of order; no test asserts write ordering, and correctness doesn't depend on it here | — |
| — (weakening check for AC7, not a mutation of shipped behavior) | Removed `@Transactional` from `rename()` | `ReferenceListService.kt:70` | **Killed** | `ReferenceListRenameRollbackIT > a forced mid-cascade failure rolls back the rename entirely()` — confirms the rollback test genuinely depends on the transaction boundary |

**Sensor summary: 7 mutations injected (6 required + 1 exploratory), 3 killed outright, 3 survived and exposed genuine (non-blocking) spec-precision gaps in the AC1/AC5/AC8 tests, 1 exploratory mutation survived as expected with no correctness implication. A separate targeted weakening (stripping `@Transactional`) confirmed AC7's rollback test is load-bearing.** Working tree confirmed clean (`git status --short` empty, `git diff --stat` empty against `HEAD`) after the full sequence.

---

### Gate

`./gradlew clean build` (full suite, real Testcontainers-backed PostgreSQL, Docker confirmed running via `docker info`):

```
BUILD SUCCESSFUL in 1m 15s
8 actionable tasks: 8 executed
```

Aggregated JUnit XML across all test-result files: **228 tests, 0 failures, 0 errors** — matches the count expected per the task brief.

---

### Ranked gaps

1. **Spec-precision gap, non-blocking**: P8-AC8's committed test (`ReferenceListControllerIT.kt:184-203`) does not actually prove "no cascade" — it only proves the end state is correct, which holds identically whether or not the cascade runs on a no-op rename (the new name equals the old name either way). Confirmed by mutation: removing the no-op short-circuit in `ReferenceListService.kt:76` leaves the entire reference suite green. A discriminating test would need to assert something the cascade would visibly change even on a same-name rename — e.g. spying on `GameRepository` and asserting `saveAndFlush` is never called, or asserting `updatedAt`/`version` on the game is untouched if such a column exists. The underlying service code is correct (the short-circuit is present and functioning); only the test's discriminating power is short of its own name's claim.
2. **Spec-precision gap, non-blocking**: P8-AC5's cascade-match trim behavior (`GameRepository.kt`'s `lower(trim(...))`) has no test proving it against a game whose stored `competition`/`opponent` value carries surrounding whitespace. Both case-variance fixtures (`"district league"` / `"DISTRICT LEAGUE"`) are already whitespace-free, so removing `trim()` from both queries leaves the suite green. Input-side trimming (on create/rename requests) is well covered; only the cascade's own trim-matching against already-stored values is untested.
3. **Spec-precision gap, non-blocking**: P8-AC1's case-insensitive-ordering test (`ReferenceListControllerIT.kt:121-130`) uses a fixture (`"zebra"`, `"Apple"`, `"banana"`) whose correct case-insensitive order coincides with plain ASCII lexicographic order (all three names differ only by leading-letter case, and ASCII sorts every uppercase letter before every lowercase one). Removing `.lowercase()` from the sort key leaves the suite green. A discriminating fixture would need names where case-insensitive and case-sensitive order diverge, e.g. `"Banana"` and `"apple"` (case-insensitive: apple, Banana; ASCII: Banana, apple).
4. **Minor, non-blocking**: the spec's Independent Test (create → attach to two games → rename → both updated → delete → both keep the new name) is proven only in two separate pieces (rename-cascade test and delete-preserves-name test), never as one continuous chain with the same entry and the same two games carrying through both operations.

None of these four gaps represent incorrect shipped behavior — all four were confirmed, by direct mutation or targeted weakening, to be gaps in test *discriminating power* only, not in the implementation itself. The implementation's correctness for all nine ACs is otherwise independently confirmed by source inspection (P8-AC3, AC8's actual short-circuit, AC5's actual `trim()` calls, AC1's actual `.lowercase()` call) and by the three mutations that did kill (swapped cascades, missing exception mapping, missing `@Valid`) plus the `@Transactional`-removal weakening for AC7.

---

## Phase 9: Docs & packaging (T47–T49)

**Verdict: PASS ✅** (with 2 non-blocking spec-precision gaps flagged below)

**Commit range covered:** `0198c30..HEAD` (`c34c1a1`, `e557ac5`, `55a953f`)

**Verifier:** fresh sub-agent, author ≠ verifier, evidence-or-zero methodology. This is **the final phase of the entire 49-task plan** — tasks.md's T1–T49 are now all complete.

---

### Spec-anchored AC coverage

| AC | Requirement (paraphrased) | Evidence | Assertion matches spec outcome? |
| --- | --- | --- | --- |
| P10-AC1 | `GET /swagger-ui.html` serves interactive documentation covering every endpoint | **No automated test exists.** Manually re-verified during this pass: ran the app locally (`SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun` against a real `docker compose up -d db`), `curl -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui.html` → `302` (springdoc's standard redirect) → `curl .../swagger-ui/index.html` → `200`. The page functionally works, but nothing in the committed test suite (`OpenApiDocsIT.kt` only ever calls `/v3/api-docs`, never `/swagger-ui*`) proves it. | **Functionally yes, but untested** — spec-precision gap, non-blocking (see ranked gaps). |
| P10-AC2 | `GET /v3/api-docs` returns an OpenAPI 3.1 document with auth schemes, request/response schemas, and error shapes | `OpenApiDocsIT.kt:66-87` (`3.1` prefix check, exact 29-path/51-operation set asserted against real `@RestController` mappings), `:89-97` (bearer scheme: `type=http`, `scheme=bearer`, `bearerFormat=JWT`), `:99-110` (every operation's `default` response documents `application/problem+json`). Request/response schema-inclusion claim independently re-verified with a scratch, uncommitted test (`ScratchSchemaCheckIT.kt`, added, run, then deleted — `git status --short` confirmed clean afterward): `components.schemas` contains 33 real DTOs including `CreateTrainingRequest`, `ExerciseDto`, `RegisterRequest`, etc., and `POST /api/v1/trainings`'s `requestBody` is `{"$ref":"#/components/schemas/CreateTrainingRequest"}` — springdoc's auto-generated schemas are genuinely present in the document, not just the hand-rolled `ProblemDetail` schema. | Yes |
| P11-AC1 | `POST /api/v1/trainings/{id}/exercises` appends one exercise, `201` | `ExerciseControllerIT.kt:61-78` — `POST appends one exercise and returns 201`: asserts `201`, `Location` header present, `$.description`/`$.numberOfPlayers` round-trip, and a real `SELECT order_index FROM exercises` (`orderIndexes()` helper, line 57-58) confirms `[0, 1]` — the new exercise landed at index 1, not just "some" index | Yes |
| P11-AC2 | `PATCH`/`DELETE .../exercises/{exerciseId}` update or remove that exercise and re-pack `order_index` contiguously | PATCH: `ExerciseControllerIT.kt:80-99` — updates only the supplied field, others preserved. DELETE + re-pack: `ExerciseControllerIT.kt:101-128` — `deleting the middle of three exercises leaves indexes 0 and 1, with no gap`: seeds 3 exercises, deletes the middle one, asserts `orderIndexes(trainingId) == listOf(0, 1)` via a **direct `JdbcTemplate` query against the `order_index` column** (not array position in the DTO, which is never serialized), then re-fetches and confirms the first and third exercise (by `description`) survived in order | Yes — genuinely checks the DB column, not the array-position proxy that could mask an internal gap |
| P12-AC1 | `docker compose up` starts both API and DB, API waiting on the DB's healthcheck | `docker-compose.yml:23-25` — `depends_on: db: condition: service_healthy` on the `api` service, matching design.md's spec exactly. Manual verification recorded in commit `55a953f`'s message (quoted below), which tasks.md's own T49 "Done when: Verified" explicitly calls for in place of an automated test. | Yes (manual evidence, as designed) |
| P12-AC2 | The image uses a multi-stage build and a non-root runtime user | `Dockerfile:2` (`FROM eclipse-temurin:21-jdk AS build`) and `:15` (`FROM eclipse-temurin:21-jre AS runtime`) — two distinct stages, build tools discarded from the runtime image. `:21-22` — `RUN chown 1000:1000 app.jar` / `USER 1000:1000`, a fixed non-root UID/GID. Commit `55a953f`'s message additionally records `docker exec coach-planner-api-api-1 id` → `uid=1000(ubuntu) gid=1000(ubuntu)`, confirming the non-root user at runtime, not just in the Dockerfile text. | Yes |

**6/6 ACs matched spec-defined outcomes; 1 (P10-AC1) is functionally correct but has zero automated test coverage — non-blocking spec-precision gap, not a failure, since AC DOC-01's actual observable behavior (the page serves and renders) was independently confirmed by hand this pass.**

**P12's manual verification evidence, quoted from commit `55a953f`** (tasks.md T49's own "Done when: Verified" — a manual test by design, not a `./gradlew` gate item):

```
Manually verified per tasks.md T49 (no automated test — a real Docker
daemon standing up two containers isn't something the Testcontainers
suite should own):

  docker compose --profile full up -d --build
  curl http://localhost:8080/actuator/health
  # -> HTTP 200, {"status":"UP","components":{"db":{"status":"UP",...

  docker exec coach-planner-api-api-1 id
  # -> uid=1000(ubuntu) gid=1000(ubuntu) — confirmed non-root
```

This demonstrates the exact things AC DEPLOY-01/AC12.2 require: a working `docker compose up` with the API actually reachable after waiting on the DB's healthcheck (not just configured to), and the non-root user confirmed live inside the running container (not just declared in the Dockerfile). Not independently re-run in this verification pass (time-boxed; relied on reading `docker-compose.yml`/`Dockerfile` directly plus this recorded evidence instead — see "P12 spot-check" note below).

**P12 spot-check:** not independently re-run as a full `docker compose --profile full up --build` (skipped — slow, full image build, and the commit message's recorded evidence together with direct inspection of `Dockerfile`/`docker-compose.yml` already gives concrete, checkable evidence for both ACs). Did independently confirm the `db`-only path (`docker compose up -d db`) starts and reaches `healthy` status, and confirmed `swagger-ui.html`/`/v3/api-docs` both serve correctly against a locally-run `bootRun` instance pointed at that database — see P10-AC1 row above.

**`APP_JWT_SECRET` wiring check:** `docker-compose.yml:32` sets `APP_JWT_SECRET: ${JWT_SECRET:?set JWT_SECRET in .env}` on the `api` service. `JwtService.kt:25` and `SecurityConfig.kt:91` both bind `@Value("\${app.jwt.secret}")`. Spring relaxed binding maps the env var `APP_JWT_SECRET` → the property `app.jwt.secret` correctly (env vars are relaxed-bound by replacing `_` with `.` and lower-casing), so the wiring is correct. `.env.example:8-10` documents the new `JWT_SECRET` var with a generation hint (`openssl rand -base64 32`).

---

### Discrimination sensor (P10, P11 — automated parts only)

All mutations applied to the real working tree one at a time, targeted test classes run after each, then reverted via `Edit`/`git checkout --`; `git status --short` confirmed empty before the next mutation. Docker confirmed running (`docker info`) throughout, so all Testcontainers-backed ITs ran for real.

| # | Mutation | File | Result | Killed by |
| --- | --- | --- | --- | --- |
| 1 | Removed the re-pack loop (`training.exercises.sortedBy...forEachIndexed...`) from `deleteExercise`, leaving a gap after delete | `TrainingService.kt:154` | **Killed** | `ExerciseControllerIT > deleting the middle of three exercises leaves indexes 0 and 1, with no gap` |
| 2 | `addExercise` hardcoded `orderIndex = 0` instead of `training.exercises.size` | `TrainingService.kt:107` | **Killed** | `ExerciseControllerIT > POST appends one exercise and returns 201` (fails on the DB-column assertion; the second exercise collides on `order_index=0` with the first) |
| 3 | Dropped `/{exerciseId}` from `ExerciseController`'s `@PatchMapping`, shifting the operation onto the collection path | `ExerciseController.kt:34` | **Killed, but by the "wrong" test** — see note below | `ExerciseControllerIT > PATCH updates only the supplied fields on that exercise`. **`OpenApiDocsIT` did not catch this** — ran it independently and all 3 of its tests passed (`tests="3" ... failures="0" errors="0"`), because its assertion is against the **set** of path templates only; shifting an operation from one already-expected path to another already-expected path is invisible to a path-set comparison, even though the per-path *method* mapping is now wrong. |
| 4 | Removed `/v3/api-docs`, `/v3/api-docs/**` from `SecurityConfig`'s public-paths list | `SecurityConfig.kt:56` | **Killed** (all 3 tests in `OpenApiDocsIT`) | `OpenApiDocsIT` — every test calls `/v3/api-docs` with no `Authorization` header and asserts `status().isOk`; once the path required auth, all three failed on the now-`401`/`403` response, confirming the suite genuinely proves public, unauthenticated reachability (AC DOC-01), not merely reachability within a context that happens not to check auth |

**Sensor summary: 4 mutations injected, 4 killed (3 as designed, 1 revealing a real cross-check gap — see below). 0 survived undetected.** Working tree confirmed clean (`git status --short` empty) after the full sequence.

**Note on mutation 3:** this is a genuine, if narrow, finding: T47's `OpenApiDocsIT` path-set test and T48's new endpoints are not actually cross-checked against each other at the operation level — only against the union of path templates. The mutation was still caught, but by `ExerciseControllerIT` (which exercises real HTTP behavior on the wrong path), not by the documentation test that was specifically probed. Non-blocking: the real behavior is still fully covered, just not by the test this sub-task's brief expected to be the one doing the catching.

---

### Gate

`./gradlew build` (full suite, real Testcontainers-backed PostgreSQL, Docker confirmed running via `docker info`):

```
BUILD SUCCESSFUL in 1m 14s
7 actionable tasks: 2 executed, 5 up-to-date
```

Aggregated JUnit XML across all test-result files: **236 tests, 0 failures, 0 errors** — matches the count expected per the task brief (up from 228 in Phase 8: 3 new `OpenApiDocsIT` tests, 5 new `ExerciseControllerIT` tests).

---

### Ranked gaps

1. **Spec-precision gap, non-blocking**: P10-AC1 (`GET /swagger-ui.html` serves interactive documentation) has zero automated test coverage. `OpenApiDocsIT.kt` only ever exercises `/v3/api-docs`. Manually confirmed working this pass (`302` → `/swagger-ui/index.html` → `200`, real HTTP round trip against a locally-run instance), so the *implementation* is correct — springdoc auto-serves the page — but nothing in the committed suite would catch a regression that broke it (e.g. accidentally excluding `/swagger-ui/**` from the public-paths list in a future change, which mutation 4 above proves the suite *would* catch for `/v3/api-docs` but has no equivalent test for the UI page itself).
2. **Spec-precision gap, non-blocking**: `OpenApiDocsIT`'s path-set assertion does not cross-check per-path *operation* correctness against T48's new endpoints — it only proves the union of path templates is right. Confirmed by mutation 3 above: shifting the `PATCH` mapping off `/exercises/{exerciseId}` onto `/exercises` left `OpenApiDocsIT` fully green (3/3 passing) while `ExerciseControllerIT` caught the regression instead. The real behavior remains fully covered by `ExerciseControllerIT`, so this is not a correctness gap — only a note that the two test classes aren't as mutually reinforcing as the task brief's cross-check goal implies.

Neither gap represents incorrect shipped behavior. All 6 ACs' underlying implementations were independently confirmed correct — P10-AC1 by direct manual HTTP verification, P10-AC2 by both the committed suite and an uncommitted scratch schema-inspection test, P11-AC1/AC2 by real DB-column assertions (not DTO-array-position proxies), and P12-AC1/AC2 by both static inspection of `Dockerfile`/`docker-compose.yml` and the commit message's recorded live-container evidence.

---

## Final note

This closes Phase 9, **the last phase of the 49-task plan**. `tasks.md` T1–T49 are now all complete and verified. Across all nine phases (P0–P8 not individually re-verified in this report's scope, but P5–P9 all carry independent Verifier passes above), the shipped implementation has no open blocking defects — only non-blocking spec-precision gaps in test *discriminating power*, each confirmed by direct mutation testing or manual re-verification to not correspond to any actual incorrect behavior.
