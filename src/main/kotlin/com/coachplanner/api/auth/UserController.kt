package com.coachplanner.api.auth

import com.coachplanner.api.auth.dto.UpdateProfileRequest
import com.coachplanner.api.auth.dto.UserDto
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val authService: AuthService) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): UserDto = authService.getProfile(currentUserId(jwt))

    @PatchMapping("/me")
    fun updateMe(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: UpdateProfileRequest): UserDto =
        authService.updateProfile(currentUserId(jwt), request)

    private fun currentUserId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
