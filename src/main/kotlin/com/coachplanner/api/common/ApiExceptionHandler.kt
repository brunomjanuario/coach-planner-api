package com.coachplanner.api.common

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.net.URI

private const val PROBLEM_BASE = "https://coachplanner.dev/problems"

/** RFC 9457 `application/problem+json` for every endpoint (AD-109) — no controller builds its own error body. */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, ex.type, ex.message)

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, ex.type, ex.message)

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ProblemDetail = problem(HttpStatus.CONFLICT, ex.type, ex.message)

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ProblemDetail = problem(HttpStatus.UNAUTHORIZED, ex.type, ex.message)

    /** AC ERR-02: a field-keyed `errors` extension member, not just a generic message. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problemDetail = problem(HttpStatus.BAD_REQUEST, "validation-failed", "One or more fields are invalid.")
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "is invalid") }
        problemDetail.setProperty("errors", errors)
        return problemDetail
    }

    /** AC ERR-04: the DB is unreachable mid-request — a 503, never a bare 500. */
    @ExceptionHandler(DataAccessResourceFailureException::class)
    fun handleDatabaseUnavailable(ex: DataAccessResourceFailureException): ProblemDetail {
        log.error("Database unavailable", ex)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "database-unavailable", "The database is temporarily unavailable.")
    }

    /** AC ERR-05: a stale @Version write loses the race — 409, not a crash. */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleStaleVersion(ex: OptimisticLockingFailureException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "stale-version", "This record was modified by someone else. Reload and try again.")

    /** AC ERR-06: an unparseable path variable (e.g. a bogus UUID) is a 400, never a 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "malformed-parameter", "The '${ex.name}' parameter is malformed.")

    /**
     * A malformed request body — including a string that doesn't match any
     * enum constant (e.g. position: "STRIKER", T24) — throws this from the
     * message-converter layer before the controller method (and @Valid)
     * ever run. Without this handler it would fall through to the
     * catch-all below and surface as a 500, since HttpMessageNotReadableException
     * is-a Exception and Spring's own default 400 mapping never gets a
     * chance once a broader @ExceptionHandler(Exception::class) exists.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedBody(ex: HttpMessageNotReadableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "malformed-request-body", "The request body is malformed or contains an invalid value.")

    /**
     * AC ERR-03: the last-resort catch-all. The stack trace is logged (with
     * the request's correlation id, via CorrelationIdFilter's MDC context —
     * automatic in every structured log line, not threaded through here)
     * but never reaches the response: no exception class name, no message,
     * no SQL fragment.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred.")
    }

    private fun problem(status: HttpStatus, typeSlug: String, detail: String?): ProblemDetail {
        val problemDetail = ProblemDetail.forStatus(status)
        problemDetail.type = URI.create("$PROBLEM_BASE/$typeSlug")
        problemDetail.title = status.reasonPhrase
        problemDetail.detail = detail
        return problemDetail
    }
}
