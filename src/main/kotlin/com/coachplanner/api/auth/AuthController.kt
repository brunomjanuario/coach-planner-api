package com.coachplanner.api.auth

import com.coachplanner.api.auth.dto.AuthResponse
import com.coachplanner.api.auth.dto.LoginRequest
import com.coachplanner.api.auth.dto.RefreshRequest
import com.coachplanner.api.auth.dto.RegisterRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.login(request))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.refresh(request.refreshToken))

    /**
     * @AuthenticationPrincipal Jwt + UUID.fromString(jwt.subject) inline
     * here and in UserController — T21's common/CurrentUser.kt formalizes
     * this into one shared resolver once a third controller needs it,
     * same minimal-now-formalize-later pattern as SecurityConfig's rollout.
     */
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<Void> {
        authService.logout(UUID.fromString(jwt.subject))
        return ResponseEntity.noContent().build()
    }
}
