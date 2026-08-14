package com.coachplanner.api.team

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

/** tasks.md T24 / AC PLAY-06…09. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PlayerControllerIT @Autowired constructor(
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

    private fun createTeam(token: String): String {
        val response = mockMvc.perform(
            post("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sub-11"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    private fun createPlayer(token: String, teamId: String, name: String = "João", shirtNumber: Int = 9): String {
        val response = mockMvc.perform(
            post("/api/v1/teams/$teamId/players")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","shirtNumber":$shirtNumber}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    @Test
    fun `a valid player create returns 201`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)

        mockMvc.perform(
            post("/api/v1/teams/$teamId/players")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"João","age":15,"shirtNumber":9,"position":"ST"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.name").value("João"))
            .andExpect(jsonPath("$.age").value(15))
            .andExpect(jsonPath("$.shirtNumber").value(9))
            .andExpect(jsonPath("$.position").value("ST"))
            .andExpect(jsonPath("$.teamId").value(teamId))
    }

    @Test
    fun `shirtNumber 0 is rejected with 400`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)

        mockMvc.perform(
            post("/api/v1/teams/$teamId/players")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"João","shirtNumber":0}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `shirtNumber 100 is rejected with 400`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)

        mockMvc.perform(
            post("/api/v1/teams/$teamId/players")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"João","shirtNumber":100}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `age 3 is rejected with 400`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)

        mockMvc.perform(
            post("/api/v1/teams/$teamId/players")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"João","age":3}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `age 100 is rejected with 400`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)

        mockMvc.perform(
            post("/api/v1/teams/$teamId/players")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"João","age":100}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `an unrecognised position is rejected with 400 and nothing is persisted`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)

        mockMvc.perform(
            post("/api/v1/teams/$teamId/players")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"João","position":"STRIKER"}"""),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(get("/api/v1/teams/$teamId").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(jsonPath("$.players.length()").value(0))
    }

    @Test
    fun `a stats-only PATCH updates goals, assists and concededGoals`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)
        val playerId = createPlayer(token, teamId)

        mockMvc.perform(
            patch("/api/v1/teams/$teamId/players/$playerId")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"goals":3,"assists":1,"concededGoals":2}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.goals").value(3))
            .andExpect(jsonPath("$.assists").value(1))
            .andExpect(jsonPath("$.concededGoals").value(2))
            .andExpect(jsonPath("$.name").value("João"))
    }
}
