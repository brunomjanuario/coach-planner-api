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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * tasks.md T33 / AC GAME-01, GAME-02. Scores can only be set through
 * `PUT /games/{id}/result` (T34, not yet built when this task lands) or
 * directly via the repository — this test uses the repository to seed a
 * recorded result, since it is testing the read/filter path, not the write
 * path that endpoint owns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class GameReadsIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val gameRepository: GameRepository,
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

    private fun createGame(token: String, teamId: String?, date: String): String {
        val teamField = if (teamId != null) "\"teamId\":\"$teamId\"," else ""
        val response = mockMvc.perform(
            post("/api/v1/games")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{$teamField"opponent":"Benfica","date":"$date","isHome":true}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    private fun createTeam(token: String): String {
        val response = mockMvc.perform(
            post("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sub-11"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    private fun getAll(token: String, query: String = ""): List<JsonNode> {
        val response = mockMvc.perform(get("/api/v1/games$query").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return jsonMapper.readTree(response).toList()
    }

    @Test
    fun `a recorded 0-0 game is classified as played, not scheduled — the null-vs-zero trap`() {
        val token = registerAndGetToken()
        val id = createGame(token, null, "2030-01-01T15:00:00Z")

        val game = gameRepository.findById(java.util.UUID.fromString(id)).orElseThrow()
        game.usScore = 0
        game.themScore = 0
        gameRepository.saveAndFlush(game)

        val played = getAll(token, "?status=played")
        val scheduled = getAll(token, "?status=scheduled")

        assert(played.any { it["id"].asString() == id }) { "expected the 0-0 game to appear under ?status=played" }
        assert(scheduled.none { it["id"].asString() == id }) { "expected the 0-0 game to NOT appear under ?status=scheduled" }
    }

    @Test
    fun `a game with no result appears under scheduled, not played`() {
        val token = registerAndGetToken()
        val id = createGame(token, null, "2030-01-01T15:00:00Z")

        val played = getAll(token, "?status=played")
        val scheduled = getAll(token, "?status=scheduled")

        assert(scheduled.any { it["id"].asString() == id })
        assert(played.none { it["id"].asString() == id })
    }

    @Test
    fun `assigned=false returns only games whose teamId is null`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)
        createGame(token, teamId, "2030-01-01T15:00:00Z")
        val unassignedId = createGame(token, null, "2030-01-02T15:00:00Z")

        val unassigned = getAll(token, "?assigned=false")

        assert(unassigned.size == 1) { "expected 1 unassigned game, got ${unassigned.size}" }
        assert(unassigned[0]["id"].asString() == unassignedId)
    }

    @Test
    fun `GET games returns the caller's games ordered by date descending`() {
        val token = registerAndGetToken()
        createGame(token, null, "2030-01-01T15:00:00Z")
        createGame(token, null, "2030-03-01T15:00:00Z")
        createGame(token, null, "2030-02-01T15:00:00Z")

        val games = getAll(token)

        assert(games.map { it["date"].asString() } == listOf("2030-03-01T15:00:00Z", "2030-02-01T15:00:00Z", "2030-01-01T15:00:00Z")) {
            "expected date-descending order, got ${games.map { it["date"] }}"
        }
    }
}
