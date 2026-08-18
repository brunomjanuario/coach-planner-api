package com.coachplanner.api.training.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** Absent means "don't touch" (same simplification as every other PATCH in this API — see T22). */
data class UpdateExerciseRequest(
    val description: String? = null,

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
