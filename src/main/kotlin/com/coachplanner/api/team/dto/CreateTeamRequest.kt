package com.coachplanner.api.team.dto

import jakarta.validation.constraints.NotBlank

data class CreateTeamRequest(
    @field:NotBlank(message = "must not be blank")
    val name: String,
    val club: String? = null,
    val season: String? = null,
)
