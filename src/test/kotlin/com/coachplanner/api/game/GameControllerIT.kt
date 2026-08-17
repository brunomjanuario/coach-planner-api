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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/** tasks.md T32 / AC GAME-03, GAME-04. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class GameControllerIT @Autowired constructor(
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

    private fun createGame(token: String, body: String): String {
        val response = mockMvc.perform(
            post("/api/v1/games")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    @Test
    fun `POST games with usScore in the body is created with null scores, ignoring it`() {
        val token = registerAndGetToken()

        val result = mockMvc.perform(
            post("/api/v1/games")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"opponent":"Benfica","date":"2030-01-01T15:00:00Z","isHome":true,"usScore":3,"themScore":1}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.opponent").value("Benfica"))
            .andExpect(jsonPath("$.isHome").value(true))
            .andExpect(jsonPath("$.usScore").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.themScore").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()

        val id = jsonMapper.readTree(result.response.contentAsString).get("id").asString()
        val location = result.response.getHeader(HttpHeaders.LOCATION)!!
        assert(location.endsWith(id)) { "expected Location to reference the new game's id: $location" }
    }

    @Test
    fun `GET games by id returns the game's full body`() {
        val token = registerAndGetToken()
        val id = createGame(token, """{"opponent":"Benfica","competition":"District League","date":"2030-01-01T15:00:00Z","isHome":true}""")

        mockMvc.perform(get("/api/v1/games/$id").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.opponent").value("Benfica"))
            .andExpect(jsonPath("$.competition").value("District League"))
            .andExpect(jsonPath("$.date").value("2030-01-01T15:00:00Z"))
            .andExpect(jsonPath("$.isHome").value(true))
            .andExpect(jsonPath("$.usScore").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.themScore").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `PATCH games with usScore is rejected with 400`() {
        val token = registerAndGetToken()
        val id = createGame(token, """{"opponent":"Benfica","date":"2030-01-01T15:00:00Z","isHome":true}""")

        mockMvc.perform(
            patch("/api/v1/games/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"usScore":3}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH games with themScore is rejected with 400`() {
        val token = registerAndGetToken()
        val id = createGame(token, """{"opponent":"Benfica","date":"2030-01-01T15:00:00Z","isHome":true}""")

        mockMvc.perform(
            patch("/api/v1/games/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"themScore":1}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH games without score fields updates supplied fields normally`() {
        val token = registerAndGetToken()
        val id = createGame(token, """{"opponent":"Benfica","date":"2030-01-01T15:00:00Z","isHome":true}""")

        mockMvc.perform(
            patch("/api/v1/games/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"opponent":"Sporting"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.opponent").value("Sporting"))
    }
}
