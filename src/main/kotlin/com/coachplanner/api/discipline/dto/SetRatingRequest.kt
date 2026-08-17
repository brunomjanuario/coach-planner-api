package com.coachplanner.api.discipline.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** `value: null` deletes the existing rating for the triple (AC RATE-08); a non-null value must be an integer 0-10 (AC RATE-10). */
data class SetRatingRequest(
    @field:Min(0, message = "must be between 0 and 10")
    @field:Max(10, message = "must be between 0 and 10")
    val value: Int? = null,
)
