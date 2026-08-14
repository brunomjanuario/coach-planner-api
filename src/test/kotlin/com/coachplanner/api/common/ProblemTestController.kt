package com.coachplanner.api.common

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

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
}
