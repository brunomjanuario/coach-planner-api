package com.coachplanner.api.training

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** tasks.md T31 / AC TRAIN-11. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TrainingDeleteIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val trainingRepository: TrainingRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
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
    fun `deleting a training removes its exercises and its own ratings, but leaves other trainings' ratings untouched`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))

        val trainingToDelete = trainingRepository.saveAndFlush(
            Training(ownerId = ownerId, teamId = team.id, day = Instant.now(), durationMinutes = 60).apply {
                exercises.add(Exercise(training = this, orderIndex = 0, description = "Warm-up"))
            },
        )
        val exerciseId = trainingToDelete.exercises[0].id
        val ratingForDeleted = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = trainingToDelete.id, value = 7))

        val survivingTraining = trainingRepository.saveAndFlush(
            Training(ownerId = ownerId, teamId = team.id, day = Instant.now(), durationMinutes = 60),
        )
        val ratingForSurvivor = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = survivingTraining.id, value = 5))

        mockMvc.perform(delete("/api/v1/trainings/${trainingToDelete.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNoContent)
        entityManager.clear() // Hibernate's first-level cache doesn't know about the DB's own FK-driven deletes

        assertTrue(trainingRepository.findById(trainingToDelete.id).isEmpty, "expected the training itself to be deleted")
        assertFalse(
            entityManager.createQuery("select e from Exercise e where e.id = :id", Any::class.java)
                .setParameter("id", exerciseId).resultList.isNotEmpty(),
            "expected the deleted training's exercise to be gone",
        )
        assertTrue(ratingRepository.findById(ratingForDeleted.id).isEmpty, "expected the deleted training's rating to be gone")

        assertTrue(trainingRepository.findById(survivingTraining.id).isPresent, "expected the other training to survive")
        assertTrue(ratingRepository.findById(ratingForSurvivor.id).isPresent, "expected the other training's rating to be untouched")
    }
}
