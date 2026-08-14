package com.coachplanner.api.auth

import com.coachplanner.api.auth.dto.ChangePasswordRequest
import com.coachplanner.api.auth.dto.UpdateProfileRequest
import com.coachplanner.api.auth.dto.UserDto
import com.coachplanner.api.common.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val authService: AuthService) {

    @GetMapping("/me")
    fun me(@CurrentUser userId: UUID): UserDto = authService.getProfile(userId)

    @PatchMapping("/me")
    fun updateMe(@CurrentUser userId: UUID, @Valid @RequestBody request: UpdateProfileRequest): UserDto =
        authService.updateProfile(userId, request)

    @PutMapping("/me/password")
    fun changePassword(@CurrentUser userId: UUID, @Valid @RequestBody request: ChangePasswordRequest): ResponseEntity<Void> {
        authService.changePassword(userId, request)
        return ResponseEntity.noContent().build()
    }
}
