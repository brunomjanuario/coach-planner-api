package com.coachplanner.api.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    /** email is citext — this comparison is case-insensitive at the DB level regardless of query shape. */
    fun existsByEmail(email: String): Boolean
}
