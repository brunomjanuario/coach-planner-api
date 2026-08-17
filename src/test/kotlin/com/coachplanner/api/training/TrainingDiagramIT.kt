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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/**
 * tasks.md T30 / AC TRAIN-09, TRAIN-10 — the HTTP-level round trip through
 * the real `jsonb` column, which is where the mapping-layer risk actually
 * lives (Phase 1's JSON-double-encoding correction). DiagramValidatorTest
 * covers the pure clamping/rejection logic in isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TrainingDiagramIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
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

    private fun createTrainingWithExercise(token: String, exerciseJson: String) =
        mockMvc.perform(
            post("/api/v1/trainings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"day":"2024-10-24T15:00:00Z","duration":60,"exercises":[$exerciseJson]}"""),
        )

    @Test
    fun `diagram null persists and round-trips as null, never an empty object`() {
        val token = registerAndGetToken()

        createTrainingWithExercise(token, """{"description":"SSG","diagram":null}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.exercises[0]").exists())
            .andExpect(jsonPath("$.exercises[0].diagram").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `an out-of-bounds x coordinate is clamped through the real jsonb round trip`() {
        val token = registerAndGetToken()

        createTrainingWithExercise(
            token,
            """{"description":"SSG","diagram":{"v":1,"pitch":"full","shapes":[{"id":"s1","kind":"cone","x":1.7,"y":0.5}]}}""",
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.exercises[0].diagram.shapes[0].x").value(1.0))
            .andExpect(jsonPath("$.exercises[0].diagram.shapes[0].y").value(0.5))
    }

    @Test
    fun `a diagram over 8192 bytes is rejected with 400 through the real jsonb round trip`() {
        val token = registerAndGetToken()
        val hugeText = "a".repeat(9000)

        createTrainingWithExercise(
            token,
            """{"description":"SSG","diagram":{"v":1,"pitch":"full","shapes":[{"id":"s1","kind":"text","x":0.5,"y":0.5,"text":"$hugeText"}]}}""",
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `61 shapes are rejected with 400 through the API`() {
        val token = registerAndGetToken()
        val shapes = (1..61).joinToString(",") { """{"id":"s$it","kind":"cone","x":0.5,"y":0.5}""" }

        createTrainingWithExercise(token, """{"description":"SSG","diagram":{"v":1,"pitch":"full","shapes":[$shapes]}}""")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `an unrecognised shape kind is rejected with 400 through the API`() {
        val token = registerAndGetToken()

        createTrainingWithExercise(
            token,
            """{"description":"SSG","diagram":{"v":1,"pitch":"full","shapes":[{"id":"s1","kind":"spaceship","x":0.5,"y":0.5}]}}""",
        )
            .andExpect(status().isBadRequest)
    }
}
