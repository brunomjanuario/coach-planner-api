package com.coachplanner.api.game

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/** tasks.md T34 / AC GAME-05, GAME-06. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class GameResultIT @Autowired constructor(
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

    private fun createGame(token: String): String {
        val response = mockMvc.perform(
            post("/api/v1/games")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"opponent":"Benfica","date":"2030-01-01T15:00:00Z","isHome":true}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    @Test
    fun `recording a 0-0 result stores both scores as 0`() {
        val token = registerAndGetToken()
        val id = createGame(token)

        mockMvc.perform(
            put("/api/v1/games/$id/result")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usScore":0,"themScore":0}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.usScore").value(0))
            .andExpect(jsonPath("$.themScore").value(0))
    }

    @Test
    fun `clearing a result sets both scores to null but the fixture survives`() {
        val token = registerAndGetToken()
        val id = createGame(token)
        mockMvc.perform(
            put("/api/v1/games/$id/result")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usScore":3,"themScore":1}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(delete("/api/v1/games/$id/result").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.usScore").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.themScore").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.id").value(id))
    }

    @Test
    fun `a score of -1 is rejected with 400`() {
        val token = registerAndGetToken()
        val id = createGame(token)

        mockMvc.perform(
            put("/api/v1/games/$id/result")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usScore":-1,"themScore":0}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a score of 100 is rejected with 400`() {
        val token = registerAndGetToken()
        val id = createGame(token)

        mockMvc.perform(
            put("/api/v1/games/$id/result")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usScore":100,"themScore":0}"""),
        ).andExpect(status().isBadRequest)
    }
}
