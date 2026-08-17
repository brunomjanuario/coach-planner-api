package com.coachplanner.api.training.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

/**
 * `day` is a non-null Kotlin type on purpose: a missing or unparseable date
 * fails in the message converter as a 400, rather than replicating the
 * frontend's `Infinity` sort fallback (spec edge case).
 */
data class CreateTrainingRequest(
    val teamId: UUID? = null,

    val day: Instant,

    @field:Min(1, message = "must be between 1 and 480")
    @field:Max(480, message = "must be between 1 and 480")
    val duration: Int,

    @field:Valid
    val exercises: List<ExerciseRequest> = emptyList(),
)
