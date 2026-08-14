package com.coachplanner.api.auth.dto

import jakarta.validation.constraints.Email

/** Both fields optional — a PATCH updates only what's supplied (spec.md edge case: an empty body leaves the user unchanged). */
data class UpdateProfileRequest(
    val name: String? = null,

    @field:Email(message = "must be a valid email address")
    val email: String? = null,
)
