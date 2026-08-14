package com.coachplanner.api.auth

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/** tasks.md T15: issue -> verify round-trip, expired rejection, wrong-key rejection — all as 401, never 500. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class JwtVerificationIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtService: JwtService,
    @Value("\${app.jwt.secret}") private val realSecret: String,
) {

    private fun signedJwt(secret: String, subject: String, expiresAt: Instant): String {
        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
            .expirationTime(Date.from(expiresAt))
            .build()
        val signedJwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        signedJwt.sign(MACSigner(secret.toByteArray(Charsets.UTF_8)))
        return signedJwt.serialize()
    }

    @Test
    fun `an access token issued by JwtService round-trips through the real resource-server filter chain`() {
        val userId = newId()
        val token = jwtService.issueAccessToken(userId)

        mockMvc.perform(get("/__test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(content().string(userId.toString()))
    }

    @Test
    fun `an expired token is rejected with 401, never 500`() {
        val expired = signedJwt(realSecret, newId().toString(), Instant.now().minus(1, ChronoUnit.MINUTES))

        mockMvc.perform(get("/__test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer $expired"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a token signed with a different key is rejected with 401, never 500`() {
        val wrongKeySecret = "a-completely-different-secret-that-does-not-match-app-jwt-secret!!"
        val wrongKeyToken = signedJwt(wrongKeySecret, newId().toString(), Instant.now().plus(15, ChronoUnit.MINUTES))

        mockMvc.perform(get("/__test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer $wrongKeyToken"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `no token at all is rejected with 401`() {
        mockMvc.perform(get("/__test/whoami")).andExpect(status().isUnauthorized)
    }
}
