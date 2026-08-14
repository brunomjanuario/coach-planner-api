package com.coachplanner.api.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    /** email is citext — these comparisons are case-insensitive at the DB level regardless of query shape. */
    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): User?
}
