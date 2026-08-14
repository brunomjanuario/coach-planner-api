package com.coachplanner.api.auth

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/** tasks.md T20 / AC AUTH-P3.4, P3.5. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ChangePasswordIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
) {

    private data class Registered(val email: String, val accessToken: String, val refreshToken: String)

    private fun register(): Registered {
        val email = "coach-${newId()}@example.com"
        val body = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Coach","email":"$email","password":"password123"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val tree = jsonMapper.readTree(body)
        return Registered(email, tree.get("accessToken").asString(), tree.get("refreshToken").asString())
    }

    private fun bearer(token: String) = "Bearer $token"

    private fun login(email: String, password: String) =
        mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""{"email":"$email","password":"$password"}"""),
        )

    @Test
    fun `changing the password succeeds, revokes the old refresh token, and the new password logs in`() {
        val user = register()

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"password123","newPassword":"newPassword456"}"""),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${user.refreshToken}"}"""),
        ).andExpect(status().isUnauthorized)

        login(user.email, "newPassword456").andExpect(status().isOk)
    }

    @Test
    fun `an incorrect current password is rejected with 400 and the stored hash is unchanged`() {
        val user = register()

        mockMvc.perform(
            put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"totally-wrong","newPassword":"newPassword456"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/incorrect-password"))

        // The original password still works — the hash was never touched.
        login(user.email, "password123").andExpect(status().isOk)
    }
}
