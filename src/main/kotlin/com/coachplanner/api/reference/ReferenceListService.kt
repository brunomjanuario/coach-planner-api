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
 * difference between them is which `games` column a rename cascades to,
 * supplied per instance as `cascadeRename`. The frontend's own AD-016
 * records what writing this twice cost: two 226-line popups differing only
 * in the noun, every fix written twice.
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
    private val cascadeRename: (ownerId: UUID, oldName: String, newName: String) -> Unit,
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

    /**
     * One transaction covers the rename and its cascade (AC COMP-05/06): a
     * failure partway through the cascade rolls back the rename itself too,
     * so no game is ever left carrying a name whose entry no longer exists
     * (AC COMP-07). A rename that resolves to the exact same trimmed name as
     * before is a true no-op — no write, no cascade (AC COMP-08). Any other
     * change, including a case-only correction, does cascade: the cascade's
     * own match against the old name is case-insensitive, but the *new*
     * stored name is exactly what the caller asked for.
     */
    @Transactional
    open fun rename(id: UUID, ownerId: UUID, name: String): ReferenceEntryDto {
        val trimmed = requireNonBlank(name)
        val entry = findOwned(id, ownerId)
        val oldName = entry.name

        if (trimmed == oldName) return ReferenceEntryDto(entry.id, entry.name)

        entry.name = trimmed
        val saved = repository.saveAndFlush(entry)
        cascadeRename(ownerId, oldName, trimmed)

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
