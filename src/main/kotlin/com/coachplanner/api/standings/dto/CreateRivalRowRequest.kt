package com.coachplanner.api.standings.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * Deliberately carries no `points`/`goalDifference` field: unknown JSON
 * properties are ignored (JacksonConfig), so a body supplying either has it
 * silently dropped — AC STAND-10 falls out of the DTO shape for free, same
 * technique as `CreateGameRequest`'s scores.
 */
data class CreateRivalRowRequest(
    @field:NotBlank(message = "must not be blank")
    val name: String,

    @field:Min(0, message = "must not be negative")
    val played: Int,

    @field:Min(0, message = "must not be negative")
    val won: Int,

    @field:Min(0, message = "must not be negative")
    val drawn: Int,

    @field:Min(0, message = "must not be negative")
    val lost: Int,

    @field:Min(0, message = "must not be negative")
    val goalsFor: Int,

    @field:Min(0, message = "must not be negative")
    val goalsAgainst: Int,
)
