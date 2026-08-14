package com.coachplanner.api.auth.dto

import jakarta.validation.constraints.NotBlank

data class RefreshRequest(
    @field:NotBlank(message = "must not be blank")
    val refreshToken: String,
)
