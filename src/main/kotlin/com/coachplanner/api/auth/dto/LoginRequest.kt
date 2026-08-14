package com.coachplanner.api.auth.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "must not be blank")
    val email: String,

    @field:NotBlank(message = "must not be blank")
    val password: String,
)
