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
