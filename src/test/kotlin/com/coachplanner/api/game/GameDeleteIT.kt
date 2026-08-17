package com.coachplanner.api.game

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.discipline.Card
import com.coachplanner.api.discipline.CardRepository
import com.coachplanner.api.discipline.CardType
import com.coachplanner.api.discipline.Rating
import com.coachplanner.api.discipline.RatingRepository
import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import jakarta.persistence.EntityManager
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

/** tasks.md T35 / AC GAME-07. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class GameDeleteIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val gameRepository: GameRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val cardRepository: CardRepository,
    private val ratingRepository: RatingRepository,
    private val entityManager: EntityManager,
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
    fun `deleting a game removes its own cards and ratings, but leaves another game's untouched`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))

        val gameToDelete = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, opponent = "Benfica", date = Instant.now(), isHome = true),
        )
        val cardForDeleted = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = gameToDelete.id, type = CardType.yellow))
        val ratingForDeleted = ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = gameToDelete.id, value = 7))

        val survivingGame = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, opponent = "Sporting", date = Instant.now(), isHome = false),
        )
        val cardForSurvivor = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = survivingGame.id, type = CardType.red))
        val ratingForSurvivor = ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = survivingGame.id, value = 5))

        mockMvc.perform(delete("/api/v1/games/${gameToDelete.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNoContent)
        entityManager.clear() // Hibernate's first-level cache doesn't know about the DB's own FK-driven deletes

        assertTrue(gameRepository.findById(gameToDelete.id).isEmpty, "expected the deleted game itself to be gone")
        assertTrue(cardRepository.findById(cardForDeleted.id).isEmpty, "expected the deleted game's card to be gone")
        assertTrue(ratingRepository.findById(ratingForDeleted.id).isEmpty, "expected the deleted game's rating to be gone")

        assertTrue(gameRepository.findById(survivingGame.id).isPresent, "expected the other game to survive")
        assertTrue(cardRepository.findById(cardForSurvivor.id).isPresent, "expected the other game's card to be untouched")
        assertTrue(ratingRepository.findById(ratingForSurvivor.id).isPresent, "expected the other game's rating to be untouched")
    }
}
