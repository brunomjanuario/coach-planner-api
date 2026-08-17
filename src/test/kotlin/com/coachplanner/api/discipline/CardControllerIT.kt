package com.coachplanner.api.discipline

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/** tasks.md T38 / AC CARD-01…05. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CardControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
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

    private fun bearer(token: String) = "Bearer $token"

    private fun createCard(token: String, playerId: UUID, gameId: UUID, type: String = "yellow") = mockMvc.perform(
        post("/api/v1/cards")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"playerId":"$playerId","gameId":"$gameId","type":"$type"}"""),
    )

    @Test
    fun `POST cards creates the card and returns 201 with a Location header`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val game = gameRepository.saveAndFlush(Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true))

        createCard(token, player.id, game.id, "yellow")
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.playerId").value(player.id.toString()))
            .andExpect(jsonPath("$.gameId").value(game.id.toString()))
            .andExpect(jsonPath("$.type").value("yellow"))
    }

    @Test
    fun `a player not in the team playing the game is rejected with 400 player-not-in-game-team`() {
        val (token, ownerId) = registerAndGetToken()
        val teamA = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val teamB = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-13"))
        val playerOnB = playerRepository.saveAndFlush(Player(team = teamB, name = "João"))
        val gameForA = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, teamId = teamA.id, opponent = "Benfica", date = Instant.now(), isHome = true),
        )

        createCard(token, playerOnB.id, gameForA.id, "yellow")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/player-not-in-game-team"))
    }

    @Test
    fun `an unassigned game rejects every player, since no team is playing it`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val unassignedGame = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, teamId = null, opponent = "Benfica", date = Instant.now(), isHome = true),
        )

        createCard(token, player.id, unassignedGame.id, "yellow")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/player-not-in-game-team"))
    }

    @Test
    fun `a card type of orange is rejected with 400`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val game = gameRepository.saveAndFlush(Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true))

        createCard(token, player.id, game.id, "orange").andExpect(status().isBadRequest)
    }

    @Test
    fun `GET cards filters by gameId alone, playerId alone, and both together`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val playerA = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val playerB = playerRepository.saveAndFlush(Player(team = team, name = "Pedro"))
        val gameA = gameRepository.saveAndFlush(Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        val gameB = gameRepository.saveAndFlush(Game(ownerId = ownerId, teamId = team.id, opponent = "Sporting", date = Instant.now(), isHome = false))

        createCard(token, playerA.id, gameA.id, "yellow").andExpect(status().isCreated)
        createCard(token, playerB.id, gameA.id, "red").andExpect(status().isCreated)
        createCard(token, playerA.id, gameB.id, "yellow").andExpect(status().isCreated)

        val byGame = getAll(token, "?gameId=${gameA.id}")
        assert(byGame.size == 2) { "expected 2 cards for gameA, got ${byGame.size}" }

        val byPlayer = getAll(token, "?playerId=${playerA.id}")
        assert(byPlayer.size == 2) { "expected 2 cards for playerA, got ${byPlayer.size}" }

        val byBoth = getAll(token, "?gameId=${gameA.id}&playerId=${playerA.id}")
        assert(byBoth.size == 1) { "expected 1 card for gameA+playerA, got ${byBoth.size}" }
    }

    @Test
    fun `DELETE cards removes the card and returns 204`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val game = gameRepository.saveAndFlush(Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        val response = createCard(token, player.id, game.id, "yellow")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        mockMvc.perform(delete("/api/v1/cards/$id").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent)

        assert(getAll(token).none { it["id"].asString() == id }) { "expected the deleted card to be gone" }
    }

    private fun getAll(token: String, query: String = ""): List<tools.jackson.databind.JsonNode> {
        val response = mockMvc.perform(get("/api/v1/cards$query").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return jsonMapper.readTree(response).toList()
    }
}
