package com.coachplanner.api.training

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** tasks.md T48 / AC EXER-02. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ExerciseControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val jdbc: JdbcTemplate,
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

    private fun createTraining(token: String, exercises: String = "[]"): String {
        val response = mockMvc.perform(
            post("/api/v1/trainings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"day":"2024-10-24T15:00:00Z","duration":90,"exercises":$exercises}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    private fun orderIndexes(trainingId: String): List<Int> =
        jdbc.queryForList("SELECT order_index FROM exercises WHERE training_id = ? ORDER BY order_index", Int::class.java, UUID.fromString(trainingId))
            .filterNotNull()

    @Test
    fun `POST appends one exercise and returns 201`() {
        val token = registerAndGetToken()
        val trainingId = createTraining(token, """[{"description":"Warm-up"}]""")

        mockMvc.perform(
            post("/api/v1/trainings/$trainingId/exercises")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"description":"Rondo","numberOfPlayers":8}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.description").value("Rondo"))
            .andExpect(jsonPath("$.numberOfPlayers").value(8))

        assert(orderIndexes(trainingId) == listOf(0, 1)) { "expected the new exercise appended at index 1, got ${orderIndexes(trainingId)}" }
    }

    @Test
    fun `PATCH updates only the supplied fields on that exercise`() {
        val token = registerAndGetToken()
        val trainingId = createTraining(token, """[{"description":"Warm-up","numberOfPlayers":10}]""")
        val trainingResponse = mockMvc.perform(
            patch("/api/v1/trainings/$trainingId").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andReturn().response.contentAsString
        val exerciseId = jsonMapper.readTree(trainingResponse).get("exercises").get(0).get("id").asString()

        mockMvc.perform(
            patch("/api/v1/trainings/$trainingId/exercises/$exerciseId")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"description":"Warm-up (extended)"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("Warm-up (extended)"))
            .andExpect(jsonPath("$.numberOfPlayers").value(10))
    }

    @Test
    fun `deleting the middle of three exercises leaves indexes 0 and 1, with no gap`() {
        val token = registerAndGetToken()
        val trainingId = createTraining(
            token,
            """[{"description":"Warm-up"},{"description":"Rondo"},{"description":"SSG"}]""",
        )
        val trainingResponse = mockMvc.perform(
            patch("/api/v1/trainings/$trainingId").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andReturn().response.contentAsString
        val exercises = jsonMapper.readTree(trainingResponse).get("exercises").toList()
        val middleExerciseId = exercises[1].get("id").asString()

        assert(orderIndexes(trainingId) == listOf(0, 1, 2))

        mockMvc.perform(delete("/api/v1/trainings/$trainingId/exercises/$middleExerciseId").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent)

        assert(orderIndexes(trainingId) == listOf(0, 1)) { "expected indexes re-packed to [0, 1] with no gap, got ${orderIndexes(trainingId)}" }

        val remaining = mockMvc.perform(
            patch("/api/v1/trainings/$trainingId").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andReturn().response.contentAsString
        val remainingDescriptions = jsonMapper.readTree(remaining).get("exercises").toList().map { it.get("description").asString() }
        assert(remainingDescriptions == listOf("Warm-up", "SSG")) { "expected the first and third exercise to survive in order, got $remainingDescriptions" }
    }

    @Test
    fun `a training that isn't the caller's is 404 on every exercise sub-resource verb`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()
        val trainingId = createTraining(tokenA, """[{"description":"Warm-up"}]""")

        mockMvc.perform(
            post("/api/v1/trainings/$trainingId/exercises")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"description":"Intruder"}"""),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `an unknown exerciseId within an owned training is 404`() {
        val token = registerAndGetToken()
        val trainingId = createTraining(token, """[{"description":"Warm-up"}]""")

        mockMvc.perform(delete("/api/v1/trainings/$trainingId/exercises/${newId()}").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNotFound)
    }
}
