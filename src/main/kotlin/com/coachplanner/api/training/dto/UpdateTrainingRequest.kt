package com.coachplanner.api.training.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

/**
 * Absent means "don't touch" (same simplification as Team/Player PATCH).
 * `exercises` carries the one distinction that matters here: absent leaves
 * the existing exercises alone (AC TRAIN-08), while an empty array is a
 * wholesale replacement with nothing (AC TRAIN-07).
 */
data class UpdateTrainingRequest(
    val teamId: UUID? = null,

    val day: Instant? = null,

    @field:Min(1, message = "must be between 1 and 480")
    @field:Max(480, message = "must be between 1 and 480")
    val duration: Int? = null,

    @field:Valid
    val exercises: List<ExerciseRequest>? = null,
)
