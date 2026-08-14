package com.coachplanner.api.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank(message = "must not be blank")
    val currentPassword: String,

    @field:Size(min = 8, message = "must be at least 8 characters")
    val newPassword: String,
)
