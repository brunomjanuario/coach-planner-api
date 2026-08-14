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

/** tasks.md T14 / AC AUTH-01…03: registration is public, returns a token pair, rejects duplicates and invalid input. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AuthControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
) {

    private fun uniqueEmail() = "coach-${newId()}@example.com"

    private fun registerBody(email: String, name: String = "Coach", password: String = "password123") =
        """{"name":"$name","email":"$email","password":"$password"}"""

    @Test
    fun `register returns 201 with the user and a token pair`() {
        val email = uniqueEmail()

        mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email)),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.name").value("Coach"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
    }

    @Test
    fun `registering with an email that differs only in case is rejected as a conflict`() {
        val email = uniqueEmail()
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email)))
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email.uppercase())),
        )
            .andExpect(status().isConflict)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/email-already-registered"))
    }

    @Test
    fun `a blank name is rejected with a 400 and no user is created`() {
        val before = userRepository.count()

        mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(uniqueEmail(), name = "")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.name").exists())

        assertEquals(before, userRepository.count(), "no user row should have been created")
    }

    @Test
    fun `a malformed email is rejected with a 400 and no user is created`() {
        val before = userRepository.count()

        mockMvc.perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("not-an-email")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.email").exists())

        assertEquals(before, userRepository.count(), "no user row should have been created")
    }

    @Test
    fun `a 7-character password is rejected with a 400 and no user is created`() {
        val before = userRepository.count()

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueEmail(), password = "1234567")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.password").exists())

        assertEquals(before, userRepository.count(), "no user row should have been created")
    }
}
