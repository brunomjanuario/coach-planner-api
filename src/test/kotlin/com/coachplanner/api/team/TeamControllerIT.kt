package com.coachplanner.api.team

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import org.junit.jupiter.api.Test
import java.util.UUID
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

/** tasks.md T22 / AC TEAM-01…04. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TeamControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
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

    private fun createTeam(token: String, name: String, club: String? = null, season: String? = null): String {
        val body = buildString {
            append("{\"name\":\"$name\"")
            if (club != null) append(",\"club\":\"$club\"")
            if (season != null) append(",\"season\":\"$season\"")
            append("}")
        }
        val response = mockMvc.perform(
            post("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    @Test
    fun `GET teams returns only the caller's teams`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()
        createTeam(tokenA, "A's Team")
        createTeam(tokenB, "B's Team")

        mockMvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("A's Team"))
    }

    @Test
    fun `POST teams returns 201, a Location header, and an empty players array`() {
        val token = registerAndGetToken()

        val result = mockMvc.perform(
            post("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sub-11","club":"Amadora","season":"23-24"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.name").value("Sub-11"))
            .andExpect(jsonPath("$.club").value("Amadora"))
            .andExpect(jsonPath("$.players").isArray)
            .andExpect(jsonPath("$.players.length()").value(0))
            .andReturn()

        val location = result.response.getHeader(HttpHeaders.LOCATION)!!
        val id = jsonMapper.readTree(result.response.contentAsString).get("id").asString()
        assert(location.endsWith(id)) { "expected Location to reference the new team's id: $location" }
    }

    @Test
    fun `PATCH with a partial body touches only the supplied fields`() {
        val token = registerAndGetToken()
        val id = createTeam(token, "Sub-11", club = "Amadora", season = "23-24")

        mockMvc.perform(
            patch("/api/v1/teams/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"club":"Benfica"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Sub-11"))
            .andExpect(jsonPath("$.club").value("Benfica"))
            .andExpect(jsonPath("$.season").value("23-24"))
    }

    @Test
    fun `PATCH never touches an existing player`() {
        val token = registerAndGetToken()
        val id = createTeam(token, "Sub-11")
        val team = teamRepository.findById(UUID.fromString(id)).orElseThrow()
        playerRepository.saveAndFlush(Player(team = team, name = "João", shirtNumber = 9))

        mockMvc.perform(
            patch("/api/v1/teams/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.players.length()").value(1))
            .andExpect(jsonPath("$.players[0].name").value("João"))
            .andExpect(jsonPath("$.players[0].shirtNumber").value(9))
    }

    @Test
    fun `an empty PATCH body returns 200 with the team unchanged`() {
        val token = registerAndGetToken()
        val id = createTeam(token, "Sub-11", club = "Amadora", season = "23-24")

        mockMvc.perform(
            patch("/api/v1/teams/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Sub-11"))
            .andExpect(jsonPath("$.club").value("Amadora"))
            .andExpect(jsonPath("$.season").value("23-24"))
    }
}
