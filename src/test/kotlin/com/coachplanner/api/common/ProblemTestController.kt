package com.coachplanner.api.common

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

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
}
