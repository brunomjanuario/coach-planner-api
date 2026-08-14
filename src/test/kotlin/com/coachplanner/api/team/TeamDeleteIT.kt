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
import com.coachplanner.api.training.Training
import com.coachplanner.api.training.TrainingRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * tasks.md T23 / AC TEAM-05. Fixtures below the Team itself go through
 * repositories, not the API — PlayerController/TrainingController/
 * GameController don't exist yet (T24+ and Phase 5/6 build those).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TeamDeleteIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val cardRepository: CardRepository,
    private val ratingRepository: RatingRepository,
    private val trainingRepository: TrainingRepository,
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

    @Test
    fun `deleting a team removes its players' cards and ratings, and nulls team_id on its trainings and games`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))

        val training = trainingRepository.saveAndFlush(
            Training(ownerId = ownerId, teamId = team.id, day = Instant.now(), durationMinutes = 90),
        )
        val game = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true),
        )
        val card = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = game.id, type = CardType.yellow))
        val rating = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = training.id, value = 7))

        mockMvc.perform(delete("/api/v1/teams/${team.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNoContent)
        entityManager.clear() // Hibernate's first-level cache doesn't know about the DB's own FK-driven deletes

        assertTrue(playerRepository.findById(player.id).isEmpty, "expected the player to be deleted")
        assertTrue(cardRepository.findById(card.id).isEmpty, "expected the player's card to be deleted")
        assertTrue(ratingRepository.findById(rating.id).isEmpty, "expected the player's rating to be deleted")

        val reloadedTraining = trainingRepository.findById(training.id).orElseThrow()
        assertNull(reloadedTraining.teamId, "expected the training to survive with team_id nulled, not deleted")
        val reloadedGame = gameRepository.findById(game.id).orElseThrow()
        assertNull(reloadedGame.teamId, "expected the game to survive with team_id nulled, not deleted")
    }
}
