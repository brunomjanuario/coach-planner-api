package com.coachplanner.api.team

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.discipline.Card
import com.coachplanner.api.discipline.CardRepository
import com.coachplanner.api.discipline.CardType
import com.coachplanner.api.discipline.Rating
import com.coachplanner.api.discipline.RatingRepository
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

/** tasks.md T25 / AC PLAY-10, PLAY-11. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PlayerReadDeleteIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val cardRepository: CardRepository,
    private val ratingRepository: RatingRepository,
    private val gameRepository: GameRepository,
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

    private fun bearer(token: String) = "Bearer $token"

    @Test
    fun `deleting a player returns 204 and removes their cards and ratings`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val game = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, opponent = "Benfica", date = Instant.now(), isHome = true),
        )
        val card = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = game.id, type = CardType.yellow))
        val rating = ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = game.id, value = 6))

        mockMvc.perform(
            delete("/api/v1/teams/${team.id}/players/${player.id}").header(HttpHeaders.AUTHORIZATION, bearer(token)),
        ).andExpect(status().isNoContent)
        entityManager.clear()

        assertTrue(playerRepository.findById(player.id).isEmpty, "expected the player to be deleted")
        assertTrue(cardRepository.findById(card.id).isEmpty, "expected the player's card to be deleted")
        assertTrue(ratingRepository.findById(rating.id).isEmpty, "expected the player's rating to be deleted")
    }

    @Test
    fun `a playerId that exists but under a different team in the path is a 404`() {
        val (token, ownerId) = registerAndGetToken()
        val realTeam = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Real Team"))
        val otherTeam = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Other Team"))
        val player = playerRepository.saveAndFlush(Player(team = realTeam, name = "João"))

        mockMvc.perform(
            get("/api/v1/teams/${otherTeam.id}/players/${player.id}").header(HttpHeaders.AUTHORIZATION, bearer(token)),
        ).andExpect(status().isNotFound)
    }
}
