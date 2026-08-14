package com.coachplanner.api.auth

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** tasks.md T17 / AC AUTH-06, AUTH-07: refresh rotation and reuse detection. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RefreshTokenIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val jdbc: JdbcTemplate,
) {

    private fun registerAndGetRefreshToken(email: String): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Coach","email":"$email","password":"password123"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("refreshToken").asString()
    }

    private fun loginAndGetRefreshToken(email: String): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password123"}"""),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("refreshToken").asString()
    }

    private fun refreshBody(token: String) = """{"refreshToken":"$token"}"""

    @Test
    fun `refresh issues a new pair and the old token then fails`() {
        val email = "coach-${newId()}@example.com"
        val oldToken = registerAndGetRefreshToken(email)

        val rotateResponse = mockMvc.perform(
            post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(oldToken)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn().response.contentAsString
        val newToken = jsonMapper.readTree(rotateResponse).get("refreshToken").asString()
        assertNotEquals(oldToken, newToken, "rotation must issue a genuinely new token")

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(oldToken)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `replaying an already-rotated token revokes every other still-valid token for that user`() {
        val email = "coach-${newId()}@example.com"
        val tokenA = registerAndGetRefreshToken(email)
        val tokenB = loginAndGetRefreshToken(email) // a second, independent, still-valid token for the same user

        // Rotate A -> C, then replay the now-revoked A.
        val rotateResponse = mockMvc.perform(
            post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(tokenA)),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val tokenC = jsonMapper.readTree(rotateResponse).get("refreshToken").asString()

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(tokenA)))
            .andExpect(status().isUnauthorized)

        // tokenB was never used and was still valid before the replay — it must be invalidated too.
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(tokenB)))
            .andExpect(status().isUnauthorized)

        // tokenC (the product of the legitimate rotation) is also caught by the mass revocation.
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(tokenC)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `only the SHA-256 hash of the refresh token is ever stored`() {
        val email = "coach-${newId()}@example.com"
        val rawToken = registerAndGetRefreshToken(email)
        val expectedHash = RefreshTokenService.sha256(rawToken)

        val storedHash = jdbc.queryForObject(
            "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
            String::class.java,
            expectedHash,
        )

        assertEquals(expectedHash, storedHash, "expected the SHA-256 hash to be stored under its own value")
        assertNotEquals(rawToken, storedHash, "the raw token must never be stored")
    }
}
