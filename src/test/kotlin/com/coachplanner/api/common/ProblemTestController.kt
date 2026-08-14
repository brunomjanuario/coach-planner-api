package com.coachplanner.api.common

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ValidationTestDto(
    @field:NotBlank(message = "must not be blank")
    val name: String,
    @field:Min(value = 1, message = "must be at least 1")
    val age: Int,
)

/**
 * Test-only fixture (compiled from src/test, never in the production
 * bootJar) — component-scanned automatically since it's under the app's
 * base package. Exists so ApiExceptionHandlerIT can exercise real
 * exception-to-ProblemDetail translation without a real domain controller.
 */
@RestController
class ProblemTestController {

    @GetMapping("/__test/problems/not-found")
    fun notFound(): Nothing = throw NotFoundException("nothing here")

    @GetMapping("/__test/problems/validation")
    fun validation(): Nothing = throw ValidationException("bad input")

    @GetMapping("/__test/problems/conflict")
    fun conflict(): Nothing = throw ConflictException("already exists")

    @PostMapping("/__test/problems/bean-validation")
    fun beanValidation(@Valid @RequestBody dto: ValidationTestDto): ValidationTestDto = dto

    @GetMapping("/__test/problems/db-unavailable")
    fun dbUnavailable(): Nothing = throw DataAccessResourceFailureException("connection pool exhausted")

    @GetMapping("/__test/problems/stale-version")
    fun staleVersion(): Nothing = throw OptimisticLockingFailureException("row was updated by another transaction")

    @GetMapping("/__test/problems/by-id/{id}")
    fun byId(@PathVariable id: UUID): UUID = id

    /** Deliberately leaks a SQL-shaped message in its own exception — the test asserts none of this reaches the response. */
    @GetMapping("/__test/problems/unexpected")
    fun unexpected(): Nothing =
        throw RuntimeException("SELECT password_hash FROM users WHERE email = 'leak@test.com'")
}
