package com.coachplanner.api.auth

import com.coachplanner.api.auth.dto.AuthResponse
import com.coachplanner.api.auth.dto.RegisterRequest
import com.coachplanner.api.auth.dto.UserDto
import com.coachplanner.api.common.ConflictException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val EMAIL_ALREADY_REGISTERED = "email-already-registered"

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val trimmedEmail = request.email.trim()
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw ConflictException("Email already registered.", type = EMAIL_ALREADY_REGISTERED)
        }

        val user = try {
            userRepository.saveAndFlush(
                User(
                    email = trimmedEmail,
                    name = request.name.trim(),
                    passwordHash = passwordEncoder.encode(request.password)!!,
                ),
            )
        } catch (ex: DataIntegrityViolationException) {
            // Closes the check-then-insert race the pre-check above can't fully rule out on its own.
            throw ConflictException("Email already registered.", type = EMAIL_ALREADY_REGISTERED)
        }

        return issueTokens(user)
    }

    private fun issueTokens(user: User): AuthResponse =
        AuthResponse(
            user = UserDto.from(user),
            accessToken = jwtService.issueAccessToken(user.id),
            refreshToken = refreshTokenService.issue(user),
            expiresIn = ACCESS_TOKEN_TTL_MINUTES * 60,
        )
}
