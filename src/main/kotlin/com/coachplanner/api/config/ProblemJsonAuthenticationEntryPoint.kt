package com.coachplanner.api.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.json.JsonMapper
import java.net.URI

private const val PROBLEM_BASE = "https://coachplanner.dev/problems"

/**
 * Spring Security's filter chain runs before the DispatcherServlet, so a
 * failed authentication never reaches ApiExceptionHandler's
 * @RestControllerAdvice (AD-109) — that only intercepts exceptions thrown
 * during controller invocation. This is the security-layer equivalent,
 * producing the same RFC 9457 problem+json shape instead of Spring
 * Security's default WWW-Authenticate-header-only 401.
 *
 * Distinguishes an expired token from any other JWT failure (AC AUTH-10):
 * Spring's JwtTimestampValidator doesn't expose a distinct error code for
 * "expired" — both an expired and a malformed/wrong-key token collapse to
 * OAuth2Error's "invalid_token" code — so the description text is the only
 * signal available, and Nimbus's own validator wording is what's matched
 * against (verified empirically in ProtectedPathsIT against a real expired
 * token, not assumed from documentation).
 */
class ProblemJsonAuthenticationEntryPoint(private val jsonMapper: JsonMapper) : AuthenticationEntryPoint {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        val (typeSlug, detail) = classify(authException)

        val problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED)
        problemDetail.type = URI.create("$PROBLEM_BASE/$typeSlug")
        problemDetail.title = HttpStatus.UNAUTHORIZED.reasonPhrase
        problemDetail.detail = detail

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        jsonMapper.writeValue(response.outputStream, problemDetail)
    }

    private fun classify(ex: AuthenticationException): Pair<String, String> {
        val isExpired = ex is OAuth2AuthenticationException &&
            ex.error.description?.contains("expired", ignoreCase = true) == true
        return if (isExpired) {
            "token-expired" to "The access token has expired."
        } else {
            "unauthorized" to "Authentication is required."
        }
    }
}
