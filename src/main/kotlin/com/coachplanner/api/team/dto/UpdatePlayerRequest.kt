package com.coachplanner.api.team.dto

import com.coachplanner.api.team.PlayerPosition
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** All fields optional. Includes the stats (goals/assists/concededGoals) the frontend's own popup cannot currently edit. */
data class UpdatePlayerRequest(
    val name: String? = null,

    @field:Min(4, message = "must be between 4 and 99")
    @field:Max(99, message = "must be between 4 and 99")
    val age: Int? = null,

    @field:Min(1, message = "must be between 1 and 99")
    @field:Max(99, message = "must be between 1 and 99")
    val shirtNumber: Int? = null,

    val position: PlayerPosition? = null,

    @field:Min(0, message = "must not be negative")
    val goals: Int? = null,

    @field:Min(0, message = "must not be negative")
    val assists: Int? = null,

    @field:Min(0, message = "must not be negative")
    val concededGoals: Int? = null,
)
