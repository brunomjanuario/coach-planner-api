package com.coachplanner.api.game

/**
 * Mirrors the frontend's `hasResult` (`lib/gameResult.js`) — the null-vs-zero
 * guard. Both scores must be recorded; a `0-0` counts as played. The schema's
 * `scores_recorded_together` CHECK makes a half-recorded result
 * unrepresentable, so checking either score alone would already be safe, but
 * checking both matches the frontend's own guard exactly.
 */
fun hasResult(game: Game): Boolean = game.usScore != null && game.themScore != null
