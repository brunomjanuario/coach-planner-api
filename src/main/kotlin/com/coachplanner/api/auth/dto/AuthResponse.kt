package com.coachplanner.api.auth.dto

data class AuthResponse(
    val user: UserDto,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)
