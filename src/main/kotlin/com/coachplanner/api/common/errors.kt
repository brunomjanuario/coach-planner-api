package com.coachplanner.api.common

/**
 * Common error vocabulary (AD-109). Every exception carries a `type` slug —
 * the last path segment of the RFC 9457 `type` URI `ApiExceptionHandler`
 * builds. A specific domain error (e.g. "email-already-registered") passes
 * its own slug; anything that doesn't care falls back to the generic one.
 */
sealed class ApiException(message: String, val type: String) : RuntimeException(message)

class NotFoundException(message: String, type: String = "not-found") : ApiException(message, type)

class ValidationException(message: String, type: String = "validation-failed") : ApiException(message, type)

class ConflictException(message: String, type: String = "conflict") : ApiException(message, type)
