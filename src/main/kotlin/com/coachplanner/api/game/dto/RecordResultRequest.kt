package com.coachplanner.api.game.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** Bounds mirror the `games` table's `us_score`/`them_score` CHECK constraints (design.md). */
data class RecordResultRequest(
    @field:Min(0, message = "must be between 0 and 99")
    @field:Max(99, message = "must be between 0 and 99")
    val usScore: Int,

    @field:Min(0, message = "must be between 0 and 99")
    @field:Max(99, message = "must be between 0 and 99")
    val themScore: Int,
)
