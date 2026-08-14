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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertFalse

/** tasks.md T19 / AC AUTH-P3.1…3.3: logout, GET/PATCH /users/me. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class UserControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
) {

    private data class Registered(val accessToken: String, val refreshToken: String)

    private fun register(email: String, name: String = "Coach"): Registered {
        val body = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","email":"$email","password":"password123"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val tree = jsonMapper.readTree(body)
        return Registered(accessToken = tree.get("accessToken").asString(), refreshToken = tree.get("refreshToken").asString())
    }

    private fun bearer(token: String) = "Bearer $token"

    @Test
    fun `GET users me returns the profile and its raw JSON never contains passwordHash`() {
        val email = "coach-${newId()}@example.com"
        val registered = register(email)

        val body = mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(registered.accessToken)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.name").value("Coach"))
            .andReturn().response.contentAsString

        assertFalse(body.contains("passwordHash", ignoreCase = true), "response must not name the passwordHash field: $body")
        assertFalse(body.contains("\$2a$") || body.contains("\$2b$"), "response must not contain a bcrypt hash: $body")
    }

    @Test
    fun `PATCH users me updates the supplied fields`() {
        val email = "coach-${newId()}@example.com"
        val registered = register(email)

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(registered.accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"New Name"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New Name"))
            .andExpect(jsonPath("$.email").value(email))
    }

    @Test
    fun `PATCH users me to another user's email is rejected with 409 and changes nothing`() {
        val existingEmail = "coach-${newId()}@example.com"
        register(existingEmail)

        val myEmail = "coach-${newId()}@example.com"
        val me = register(myEmail, name = "Original Name")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(me.accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Attempted Change","email":"$existingEmail"}"""),
        ).andExpect(status().isConflict)

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(me.accessToken)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Original Name"))
            .andExpect(jsonPath("$.email").value(myEmail))
    }

    @Test
    fun `logout returns 204 and revokes the refresh token`() {
        val registered = register("coach-${newId()}@example.com")

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(registered.accessToken)))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"${registered.refreshToken}"}"""),
        ).andExpect(status().isUnauthorized)
    }
}
