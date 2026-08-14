package com.coachplanner.api.auth

import com.coachplanner.api.auth.dto.AuthResponse
import com.coachplanner.api.auth.dto.LoginRequest
import com.coachplanner.api.auth.dto.RefreshRequest
import com.coachplanner.api.auth.dto.RegisterRequest
import com.coachplanner.api.common.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

    @PostMapping("/logout")
    fun logout(@CurrentUser userId: UUID): ResponseEntity<Void> {
        authService.logout(userId)
        return ResponseEntity.noContent().build()
    }
}
