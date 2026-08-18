package com.coachplanner.api.reference

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.OwnedRepository
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.reference.dto.ReferenceEntryDto
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Generic over both managed reference lists (AD-104): competitions and
 * opponents are identical in every respect this service handles — the only
 * difference between them (which `games` column a rename cascades to)
 * doesn't exist yet at this task; it's T45's addition. The frontend's own
 * AD-016 records what writing this twice cost: two 226-line popups
 * differing only in the noun, every fix written twice.
 *
 * Not a `@Service` itself — it has a type parameter, and Spring can't manage
 * a generic bean without an explicit factory. `ReferenceListConfig` supplies
 * one instance per list via `@Bean` methods.
 *
 * `open`, deliberately: the Kotlin Spring compiler plugin auto-opens classes
 * carrying `@Service`/`@Component`/etc., but this class carries none of
 * those (it's `@Bean`-instantiated, not component-scanned), so it stays
 * `final` by Kotlin's own default. A `final` class with `@Transactional`
 * methods breaks CGLIB proxying outright — Spring needs to subclass it to
 * intercept the calls — so `open` here isn't stylistic, it's required for
 * the bean to construct at all.
 */
open class ReferenceListService<T : ReferenceEntry>(
    private val repository: OwnedRepository<T, UUID>,
    private val newEntry: (ownerId: UUID, name: String) -> T,
) {

    @Transactional(readOnly = true)
    open fun getAll(ownerId: UUID): List<ReferenceEntryDto> =
        repository.findAllByOwnerId(ownerId)
            .sortedBy { it.name.lowercase() }
            .map { ReferenceEntryDto(it.id, it.name) }

    /** The trimmed name is what's stored and returned (AC COMP-04). */
    @Transactional
    open fun create(ownerId: UUID, name: String): ReferenceEntryDto {
        val trimmed = requireNonBlank(name)
        val saved = repository.saveAndFlush(newEntry(ownerId, trimmed))
        return ReferenceEntryDto(saved.id, saved.name)
    }

    /** The name stays on every historical game — no cascade, no FK, nothing to touch (AC COMP-09, AD-104). */
    @Transactional
    open fun delete(id: UUID, ownerId: UUID) {
        repository.delete(findOwned(id, ownerId))
    }

    private fun requireNonBlank(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ValidationException("must not be blank")
        return trimmed
    }

    private fun findOwned(id: UUID, ownerId: UUID): T =
        repository.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Entry not found.")
}
