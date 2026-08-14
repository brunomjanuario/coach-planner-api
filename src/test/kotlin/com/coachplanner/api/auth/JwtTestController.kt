package com.coachplanner.api.auth

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only fixture (compiled from src/test, never in the production
 * bootJar) — component-scanned automatically since it's under the app's
 * base package. Exists so JwtVerificationIT can exercise the real
 * resource-server filter chain (SecurityConfig's JwtDecoder) against a
 * protected endpoint before T19 builds the real /users/me.
 */
@RestController
class JwtTestController {

    @GetMapping("/__test/whoami")
    fun whoami(@AuthenticationPrincipal jwt: Jwt): String = jwt.subject!!
}
