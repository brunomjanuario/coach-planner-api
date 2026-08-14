package com.coachplanner.api.common

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import java.io.Serializable
import java.util.UUID

/**
 * Every repository for an entity with its own `owner_id` column extends
 * this instead of `JpaRepository` directly, so the ownership-scoped finder
 * is always at hand rather than hand-written per repository (and possibly
 * forgotten in one of them). It does not, by itself, stop someone from also
 * adding an unscoped `@Query` or relying on inherited `findById` — that
 * discipline is enforced by code review and OwnershipIsolationIT, not the
 * type system alone.
 */
@NoRepositoryBean
interface OwnedRepository<T : Any, ID : Serializable> : JpaRepository<T, ID> {
    fun findByIdAndOwnerId(id: ID, ownerId: UUID): T?

    fun findAllByOwnerId(ownerId: UUID): List<T>
}
