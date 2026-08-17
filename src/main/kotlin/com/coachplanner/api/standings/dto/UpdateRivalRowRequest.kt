package com.coachplanner.api.standings.dto

import jakarta.validation.constraints.Min

/** Absent means "don't touch" (same simplification as Team/Player/Training PATCH). */
data class UpdateRivalRowRequest(
    val name: String? = null,

    @field:Min(0, message = "must not be negative")
    val played: Int? = null,

    @field:Min(0, message = "must not be negative")
    val won: Int? = null,

    @field:Min(0, message = "must not be negative")
    val drawn: Int? = null,

    @field:Min(0, message = "must not be negative")
    val lost: Int? = null,

    @field:Min(0, message = "must not be negative")
    val goalsFor: Int? = null,

    @field:Min(0, message = "must not be negative")
    val goalsAgainst: Int? = null,
)
