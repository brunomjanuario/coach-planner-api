package com.coachplanner.api.discipline

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import com.coachplanner.api.training.Training
import com.coachplanner.api.training.TrainingRepository
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
import java.time.Instant
import java.util.UUID

/**
 * tasks.md T39 / AC RATE-06. Ratings are seeded through the repository
 * directly rather than through HTTP — the write endpoint (`PUT
 * /ratings/{eventType}/{eventId}/players/{playerId}`) is T40's scope and
 * doesn't exist yet; this task is testing the read/mapping path only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RatingReadsIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val trainingRepository: TrainingRepository,
    private val gameRepository: GameRepository,
    private val ratingRepository: RatingRepository,
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

    private fun getAll(token: String, query: String = ""): List<JsonNode> {
        val response = mockMvc.perform(get("/api/v1/ratings$query").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return jsonMapper.readTree(response).toList()
    }

    @Test
    fun `a training-rating serializes with eventType training and eventId equal to the training id`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))
        ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = training.id, value = 7))

        val ratings = getAll(token)

        assert(ratings.size == 1)
        assert(ratings[0]["eventType"].asString() == "training")
        assert(ratings[0]["eventId"].asString() == training.id.toString())
        assert(ratings[0]["value"].asInt() == 7)
    }

    @Test
    fun `a game-rating serializes with eventType game and eventId equal to the game id`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val game = gameRepository.saveAndFlush(Game(ownerId = ownerId, opponent = "Benfica", date = Instant.now(), isHome = true))
        ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = game.id, value = 4))

        val ratings = getAll(token)

        assert(ratings.size == 1)
        assert(ratings[0]["eventType"].asString() == "game")
        assert(ratings[0]["eventId"].asString() == game.id.toString())
        assert(ratings[0]["value"].asInt() == 4)
    }

    @Test
    fun `filtering by eventType, eventId and playerId each work`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val playerA = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val playerB = playerRepository.saveAndFlush(Player(team = team, name = "Pedro"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))
        val game = gameRepository.saveAndFlush(Game(ownerId = ownerId, opponent = "Benfica", date = Instant.now(), isHome = true))

        ratingRepository.saveAndFlush(Rating(playerId = playerA.id, trainingId = training.id, value = 7))
        ratingRepository.saveAndFlush(Rating(playerId = playerB.id, trainingId = training.id, value = 6))
        ratingRepository.saveAndFlush(Rating(playerId = playerA.id, gameId = game.id, value = 4))

        val byEventType = getAll(token, "?eventType=training")
        assert(byEventType.size == 2) { "expected 2 training ratings, got ${byEventType.size}" }

        val byEventId = getAll(token, "?eventId=${game.id}")
        assert(byEventId.size == 1) { "expected 1 rating for the game id, got ${byEventId.size}" }

        val byPlayer = getAll(token, "?playerId=${playerA.id}")
        assert(byPlayer.size == 2) { "expected 2 ratings for playerA, got ${byPlayer.size}" }
    }
}
