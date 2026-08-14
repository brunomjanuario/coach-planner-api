package com.coachplanner.api.team.dto

import com.coachplanner.api.team.PlayerPosition
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreatePlayerRequest(
    @field:NotBlank(message = "must not be blank")
    val name: String,

    @field:Min(4, message = "must be between 4 and 99")
    @field:Max(99, message = "must be between 4 and 99")
    val age: Int? = null,

    @field:Min(1, message = "must be between 1 and 99")
    @field:Max(99, message = "must be between 1 and 99")
    val shirtNumber: Int? = null,

    /** Deliberately stricter than the frontend's free-text field — an unrecognised value fails JSON parsing itself, before @Valid even runs. */
    val position: PlayerPosition? = null,
)
