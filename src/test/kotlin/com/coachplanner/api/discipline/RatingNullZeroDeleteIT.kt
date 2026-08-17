package com.coachplanner.api.discipline

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/** tasks.md T41 / AC RATE-08…11 and the spec's Independent Test (5 -> 0 -> null). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RatingNullZeroDeleteIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val trainingRepository: TrainingRepository,
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

    private fun putRating(token: String, eventId: UUID, playerId: UUID, valueJson: String) = mockMvc.perform(
        put("/api/v1/ratings/training/$eventId/players/$playerId")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"value":$valueJson}"""),
    )

    @Test
    fun `the spec's three-state walk — 5, then 0, then null — leaves three distinct observable states`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))

        putRating(token, training.id, player.id, "5")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value").value(5))
        assert(ratingRepository.findByPlayerIdAndTrainingId(player.id, training.id)?.value?.toInt() == 5)

        putRating(token, training.id, player.id, "0")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value").value(0))
        assert(
            ratingRepository.findByPlayerIdAndTrainingId(player.id, training.id)?.value?.toInt() == 0,
        ) { "expected a stored, readable rating of 0 — not absent" }

        putRating(token, training.id, player.id, "null")
            .andExpect(status().isNoContent)
        assert(
            ratingRepository.findByPlayerIdAndTrainingId(player.id, training.id) == null,
        ) { "expected no row after setting value to null" }
    }

    @Test
    fun `a non-integer value is rejected with 400`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))

        putRating(token, training.id, player.id, "5.5").andExpect(status().isBadRequest)
    }

    @Test
    fun `a value of -1 is rejected with 400`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))

        putRating(token, training.id, player.id, "-1")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.value").value("must be between 0 and 10"))
    }

    @Test
    fun `a value of 11 is rejected with 400`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))

        putRating(token, training.id, player.id, "11")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.value").value("must be between 0 and 10"))
    }

    @Test
    fun `setting a rating to null when none exists is a no-op 204, not an error`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))

        putRating(token, training.id, player.id, "null").andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE ratings by id removes it and returns 204`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))
        val rating = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = training.id, value = 6))

        mockMvc.perform(delete("/api/v1/ratings/${rating.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNoContent)

        assert(ratingRepository.findById(rating.id).isEmpty)
    }
}
