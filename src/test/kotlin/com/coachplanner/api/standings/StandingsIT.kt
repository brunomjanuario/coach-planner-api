package com.coachplanner.api.standings

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/** tasks.md T37 / AC STAND-11, STAND-12 — the HTTP-level end of the combined table, on top of StandingsCalculatorTest's pure-logic coverage. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class StandingsIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
) {

    private fun registerAndGetToken(): Pair<String, UUID> {
        val email = "coach-${newId()}@example.com"
        val body = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Coach","email":"$email","password":"password123"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val tree = jsonMapper.readTree(body)
        return tree.get("accessToken").asString() to UUID.fromString(tree.get("user").get("id").asString())
    }

    @Test
    fun `a team with no played games appears as an all-zero row, present and never absent`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))

        mockMvc.perform(get("/api/v1/standings?teamId=${team.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Sub-11"))
            .andExpect(jsonPath("$[0].played").value(0))
            .andExpect(jsonPath("$[0].points").value(0))
            .andExpect(jsonPath("$[0].isOurs").value(true))
    }

    @Test
    fun `recording a 0-0 draw contributes a draw and one point to the team's own row`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        gameRepository.saveAndFlush(
            Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true, usScore = 0, themScore = 0),
        )

        mockMvc.perform(get("/api/v1/standings?teamId=${team.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].played").value(1))
            .andExpect(jsonPath("$[0].drawn").value(1))
            .andExpect(jsonPath("$[0].points").value(1))
    }

    @Test
    fun `the combined table includes rival rows sorted alongside the team's own row`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Own Team"))
        gameRepository.saveAndFlush(
            Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true, usScore = 1, themScore = 0),
        )

        mockMvc.perform(
            post("/api/v1/standings/rivals")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Rival","played":1,"won":1,"drawn":0,"lost":0,"goalsFor":5,"goalsAgainst":0}"""),
        ).andExpect(status().isCreated)

        // Own team: 1 win, 1 goal for = 3 points, GD +1. Rival: 1 win, 5 goals for = 3 points, GD +5.
        // Same points, rival's higher goal difference puts it first.
        mockMvc.perform(get("/api/v1/standings?teamId=${team.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Rival"))
            .andExpect(jsonPath("$[0].isOurs").value(false))
            .andExpect(jsonPath("$[1].name").value("Own Team"))
            .andExpect(jsonPath("$[1].isOurs").value(true))
    }

    @Test
    fun `a scheduled fixture with no result contributes nothing to the team's own row`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        gameRepository.saveAndFlush(
            Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true),
        )

        mockMvc.perform(get("/api/v1/standings?teamId=${team.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].played").value(0))
    }
}
