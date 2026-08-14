package com.coachplanner.api.auth

import com.coachplanner.api.auth.dto.AuthResponse
import com.coachplanner.api.auth.dto.ChangePasswordRequest
import com.coachplanner.api.auth.dto.LoginRequest
import com.coachplanner.api.auth.dto.RegisterRequest
import com.coachplanner.api.auth.dto.UpdateProfileRequest
import com.coachplanner.api.auth.dto.UserDto
import com.coachplanner.api.common.ConflictException
import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.UnauthorizedException
import com.coachplanner.api.common.ValidationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val EMAIL_ALREADY_REGISTERED = "email-already-registered"
private const val INVALID_CREDENTIALS = "invalid-credentials"
private const val INVALID_CREDENTIALS_MESSAGE = "Invalid email or password."
private const val INCORRECT_PASSWORD = "incorrect-password"

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

    /**
     * Password verification always runs, even when the email is unknown —
     * against a fixed dummy hash computed once — so a coach probing for
     * valid accounts can't distinguish "wrong password" from "no such
     * account" by response time. Both failure paths throw the identical
     * exception (same message, same type), so the resulting problem+json
     * bodies are byte-identical (AC AUTH-05).
     */
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email.trim())
        val hashToCheck = user?.passwordHash ?: dummyHash
        val passwordMatches = passwordEncoder.matches(request.password, hashToCheck)

        if (user == null || !passwordMatches) {
            throw UnauthorizedException(INVALID_CREDENTIALS_MESSAGE, type = INVALID_CREDENTIALS)
        }

        return issueTokens(user)
    }

    private val dummyHash: String by lazy { passwordEncoder.encode("dummy-password-for-timing-parity")!! }

    /** Rotation is RefreshTokenService's job; this only assembles the new pair once rotation succeeds. */
    @Transactional
    fun refresh(rawRefreshToken: String): AuthResponse {
        val userId = refreshTokenService.rotate(rawRefreshToken)
        val user = userRepository.findById(userId)
            .orElseThrow { UnauthorizedException(INVALID_CREDENTIALS_MESSAGE, type = INVALID_CREDENTIALS) }
        return issueTokens(user)
    }

    /** Revokes every refresh token for the user — the access token itself keeps working until it naturally expires (15 min). */
    fun logout(userId: UUID) {
        refreshTokenService.revokeAllForUser(userId)
    }

    fun getProfile(userId: UUID): UserDto =
        UserDto.from(userRepository.findById(userId).orElseThrow { NotFoundException("User not found.") })

    /**
     * Email uniqueness is checked, and the check's own exception thrown,
     * *before* either field is mutated on the entity — a rejected PATCH
     * leaves the user genuinely untouched, not just rolled back at the
     * transaction boundary (AC AUTH-P3.3).
     */
    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserDto {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found.") }

        val newEmail = request.email?.trim()
        if (newEmail != null && newEmail != user.email && userRepository.existsByEmail(newEmail)) {
            throw ConflictException("Email already registered.", type = EMAIL_ALREADY_REGISTERED)
        }

        request.name?.let { user.name = it.trim() }
        newEmail?.let { user.email = it }

        return try {
            UserDto.from(userRepository.saveAndFlush(user))
        } catch (ex: DataIntegrityViolationException) {
            throw ConflictException("Email already registered.", type = EMAIL_ALREADY_REGISTERED)
        }
    }

    /**
     * The current-password check happens before any mutation — a wrong
     * current password leaves the stored hash genuinely unchanged, not
     * just rolled back (AC AUTH-P3.5). Revoking every refresh token forces
     * re-authentication everywhere else the old password was trusted.
     */
    @Transactional
    fun changePassword(userId: UUID, request: ChangePasswordRequest) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found.") }

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw ValidationException("Current password is incorrect.", type = INCORRECT_PASSWORD)
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)!!
        userRepository.saveAndFlush(user)
        refreshTokenService.revokeAllForUser(userId)
    }

    private fun issueTokens(user: User): AuthResponse =
        AuthResponse(
            user = UserDto.from(user),
            accessToken = jwtService.issueAccessToken(user.id),
            refreshToken = refreshTokenService.issue(user),
            expiresIn = ACCESS_TOKEN_TTL_MINUTES * 60,
        )
}
