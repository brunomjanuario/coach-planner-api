package com.coachplanner.api.standings

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/** tasks.md T36 / AC STAND-08…10. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RivalRowControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
) {

    private fun registerAndGetToken(): String {
        val email = "coach-${newId()}@example.com"
        val body = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Coach","email":"$email","password":"password123"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("accessToken").asString()
    }

    private fun bearer(token: String) = "Bearer $token"

    private fun createRival(token: String, body: String) = mockMvc.perform(
        post("/api/v1/standings/rivals")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    @Test
    fun `won plus drawn plus lost not equal to played is rejected with 400 naming the mismatch on create`() {
        val token = registerAndGetToken()

        createRival(token, """{"name":"Sporting","played":5,"won":2,"drawn":1,"lost":1,"goalsFor":10,"goalsAgainst":5}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Won, drawn and lost (4) must add up to played (5)."))
    }

    @Test
    fun `a negative figure is rejected with 400 on create`() {
        val token = registerAndGetToken()

        createRival(token, """{"name":"Sporting","played":5,"won":-1,"drawn":1,"lost":5,"goalsFor":10,"goalsAgainst":5}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.won").value("must not be negative"))
    }

    @Test
    fun `an all-zero row with played 0 is accepted`() {
        val token = registerAndGetToken()

        createRival(token, """{"name":"Sporting","played":0,"won":0,"drawn":0,"lost":0,"goalsFor":0,"goalsAgainst":0}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.played").value(0))
    }

    @Test
    fun `a body carrying points is accepted with that value ignored`() {
        val token = registerAndGetToken()

        createRival(token, """{"name":"Sporting","played":1,"won":1,"drawn":0,"lost":0,"goalsFor":3,"goalsAgainst":1,"points":99}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.points").doesNotExist())
    }

    @Test
    fun `updating a row so won plus drawn plus lost no longer equals played is rejected with 400`() {
        val token = registerAndGetToken()
        val response = createRival(token, """{"name":"Sporting","played":3,"won":1,"drawn":1,"lost":1,"goalsFor":5,"goalsAgainst":3}""")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        mockMvc.perform(
            patch("/api/v1/standings/rivals/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"won":5}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Won, drawn and lost (7) must add up to played (3)."))
    }
}
