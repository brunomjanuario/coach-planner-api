package com.coachplanner.api.auth.dto

import com.coachplanner.api.auth.User
import java.time.Instant
import java.util.UUID

/** Deliberately excludes passwordHash — there is no field to leak here, not just a hidden one (AC AUTH-P3.1). */
data class UserDto(
    val id: UUID,
    val name: String,
    val email: String,
    val createdAt: Instant?,
) {
    companion object {
        fun from(user: User) = UserDto(id = user.id, name = user.name, email = user.email, createdAt = user.createdAt)
    }
}
