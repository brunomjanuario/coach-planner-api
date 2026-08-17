package com.coachplanner.api.training.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/** Bounds mirror the `exercises` table's CHECK constraints (design.md) so the DB is the floor, not the first line of defence. */
data class ExerciseRequest(
    @field:NotBlank(message = "must not be blank")
    val description: String,

    @field:Min(1, message = "must be between 1 and 40")
    @field:Max(40, message = "must be between 1 and 40")
    val numberOfPlayers: Int? = null,

    @field:Min(1, message = "must be between 1 and 480")
    @field:Max(480, message = "must be between 1 and 480")
    val duration: Int? = null,

    @field:Min(1, message = "must be between 1 and 99")
    @field:Max(99, message = "must be between 1 and 99")
    val repetitions: Int? = null,

    val diagram: Map<String, Any?>? = null,
)
