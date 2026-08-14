package com.coachplanner.api.auth

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/** tasks.md T16 / AC AUTH-04, AUTH-05. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class LoginIT @Autowired constructor(private val mockMvc: MockMvc) {

    private fun registerBody(email: String, password: String = "password123") =
        """{"name":"Coach","email":"$email","password":"$password"}"""

    private fun loginBody(email: String, password: String) =
        """{"email":"$email","password":"$password"}"""

    private fun registerUser(email: String, password: String = "password123") {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email, password)))
            .andExpect(status().isCreated)
    }

    @Test
    fun `login with the correct pair returns 200 and a token pair`() {
        val email = "coach-${newId()}@example.com"
        registerUser(email, "password123")

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(email, "password123")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `a wrong password and an unknown email return byte-identical 401 bodies`() {
        val email = "coach-${newId()}@example.com"
        registerUser(email, "password123")

        val wrongPasswordResult = mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(email, "totally-wrong-password")),
        )
            .andExpect(status().isUnauthorized)
            .andReturn()

        val unknownEmailResult = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("no-such-user-${newId()}@example.com", "totally-wrong-password")),
        )
            .andExpect(status().isUnauthorized)
            .andReturn()

        val wrongPasswordBody = wrongPasswordResult.response.contentAsString
        val unknownEmailBody = unknownEmailResult.response.contentAsString

        assertEquals(
            wrongPasswordBody,
            unknownEmailBody,
            "wrong-password and unknown-email responses must be byte-identical, or an attacker can tell accounts apart",
        )
    }
}
