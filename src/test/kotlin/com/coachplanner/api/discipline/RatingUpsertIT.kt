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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** tasks.md T40 / AC RATE-07. The highest-risk task in this phase: concurrency must be provable, not asserted by inspection. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RatingUpsertIT @Autowired constructor(
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

    private fun putRating(token: String, eventType: String, eventId: UUID, playerId: UUID, value: Int) = mockMvc.perform(
        put("/api/v1/ratings/$eventType/$eventId/players/$playerId")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"value":$value}"""),
    )

    @Test
    fun `two sequential PUT calls leave exactly one row with the second value`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))

        putRating(token, "training", training.id, player.id, 5)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value").value(5))
        putRating(token, "training", training.id, player.id, 8)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value").value(8))

        val rows = ratingRepository.findAll().filter { it.playerId == player.id && it.trainingId == training.id }
        assertEquals(1, rows.size, "expected exactly one row for this triple")
        assertEquals(8, rows[0].value.toInt(), "expected the second call's value to have overwritten the first")
    }

    @Test
    fun `concurrent PUT calls for the same triple leave exactly one row`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = ownerId, day = Instant.now(), durationMinutes = 60))

        val threadCount = 12
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)

        val futures = (0 until threadCount).map { i ->
            executor.submit {
                ready.countDown()
                start.await()
                putRating(token, "training", training.id, player.id, i % 11)
            }
        }
        ready.await(5, TimeUnit.SECONDS)
        start.countDown()
        futures.forEach { it.get(15, TimeUnit.SECONDS) }
        executor.shutdown()

        val rows = ratingRepository.findAll().filter { it.playerId == player.id && it.trainingId == training.id }
        assertEquals(1, rows.size, "expected exactly one row for this triple even under concurrent writes, got ${rows.size}")
    }

    @Test
    fun `an eventType that is neither training nor game is rejected with 400`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))

        putRating(token, "match", newId(), player.id, 5).andExpect(status().isBadRequest)
    }

    @Test
    fun `an event that does not exist or is not the caller's is rejected with 400`() {
        val (token, ownerId) = registerAndGetToken()
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerId, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))

        putRating(token, "training", newId(), player.id, 5).andExpect(status().isBadRequest)
    }
}
