package com.coachplanner.api.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

private const val PROBLEM_BASE = "https://coachplanner.dev/problems"

/** RFC 9457 `application/problem+json` for every endpoint (AD-109) — no controller builds its own error body. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, ex.type, ex.message)

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, ex.type, ex.message)

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ProblemDetail = problem(HttpStatus.CONFLICT, ex.type, ex.message)

    private fun problem(status: HttpStatus, typeSlug: String, detail: String?): ProblemDetail {
        val problemDetail = ProblemDetail.forStatus(status)
        problemDetail.type = URI.create("$PROBLEM_BASE/$typeSlug")
        problemDetail.title = status.reasonPhrase
        problemDetail.detail = detail
        return problemDetail
    }
}
