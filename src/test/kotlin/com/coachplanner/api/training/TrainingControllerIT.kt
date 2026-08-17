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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/** tasks.md T28 / AC TRAIN-05…08, TRAIN-12. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TrainingControllerIT @Autowired constructor(
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

    private fun createTeam(token: String): String {
        val response = mockMvc.perform(
            post("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sub-11"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    private fun createTraining(token: String, body: String): String {
        val response = mockMvc.perform(
            post("/api/v1/trainings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    @Test
    fun `POST trainings persists the training and all three exercises in order`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)

        val result = mockMvc.perform(
            post("/api/v1/trainings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"teamId":"$teamId","day":"2024-10-24T15:00:00Z","duration":90,
                     "exercises":[
                       {"description":"Warm-up","numberOfPlayers":21,"duration":20,"repetitions":2},
                       {"description":"Rondo"},
                       {"description":"SSG"}]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.teamId").value(teamId))
            .andExpect(jsonPath("$.day").value("2024-10-24T15:00:00Z"))
            .andExpect(jsonPath("$.duration").value(90))
            .andExpect(jsonPath("$.exercises.length()").value(3))
            .andExpect(jsonPath("$.exercises[0].description").value("Warm-up"))
            .andExpect(jsonPath("$.exercises[0].numberOfPlayers").value(21))
            .andExpect(jsonPath("$.exercises[0].duration").value(20))
            .andExpect(jsonPath("$.exercises[0].repetitions").value(2))
            .andExpect(jsonPath("$.exercises[1].description").value("Rondo"))
            .andExpect(jsonPath("$.exercises[2].description").value("SSG"))
            .andReturn()

        val id = jsonMapper.readTree(result.response.contentAsString).get("id").asString()
        val location = result.response.getHeader(HttpHeaders.LOCATION)!!
        assert(location.endsWith(id)) { "expected Location to reference the new training's id: $location" }

        // Re-read through a fresh request: the three rows are really persisted, not just echoed back.
        mockMvc.perform(
            patch("/api/v1/trainings/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.exercises.length()").value(3))
            .andExpect(jsonPath("$.exercises[0].description").value("Warm-up"))
            .andExpect(jsonPath("$.exercises[1].description").value("Rondo"))
            .andExpect(jsonPath("$.exercises[2].description").value("SSG"))
    }

    @Test
    fun `PATCH with two exercises replaces all three`() {
        val token = registerAndGetToken()
        val id = createTraining(
            token,
            """
            {"day":"2024-10-24T15:00:00Z","duration":90,
             "exercises":[{"description":"Warm-up"},{"description":"Rondo"},{"description":"SSG"}]}
            """.trimIndent(),
        )

        mockMvc.perform(
            patch("/api/v1/trainings/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"exercises":[{"description":"Finishing"},{"description":"Cool-down"}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.exercises.length()").value(2))
            .andExpect(jsonPath("$.exercises[0].description").value("Finishing"))
            .andExpect(jsonPath("$.exercises[1].description").value("Cool-down"))
    }

    @Test
    fun `PATCH without the exercises key leaves the existing exercises intact`() {
        val token = registerAndGetToken()
        val id = createTraining(
            token,
            """
            {"day":"2024-10-24T15:00:00Z","duration":90,
             "exercises":[{"description":"Warm-up"},{"description":"Rondo"}]}
            """.trimIndent(),
        )

        mockMvc.perform(
            patch("/api/v1/trainings/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"duration":75}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duration").value(75))
            .andExpect(jsonPath("$.exercises.length()").value(2))
            .andExpect(jsonPath("$.exercises[0].description").value("Warm-up"))
            .andExpect(jsonPath("$.exercises[1].description").value("Rondo"))
    }

    @Test
    fun `PATCH with an empty exercises array clears them — absent and empty are not the same`() {
        val token = registerAndGetToken()
        val id = createTraining(
            token,
            """{"day":"2024-10-24T15:00:00Z","duration":90,"exercises":[{"description":"Warm-up"}]}""",
        )

        mockMvc.perform(
            patch("/api/v1/trainings/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"exercises":[]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.exercises.length()").value(0))
    }

    @Test
    fun `a duration of 0 or 481 is rejected with 400 on create`() {
        val token = registerAndGetToken()

        for (duration in listOf(0, 481)) {
            mockMvc.perform(
                post("/api/v1/trainings")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"day":"2024-10-24T15:00:00Z","duration":$duration}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors.duration").value("must be between 1 and 480"))
        }
    }

    @Test
    fun `a duration of 0 or 481 is rejected with 400 on update`() {
        val token = registerAndGetToken()
        val id = createTraining(token, """{"day":"2024-10-24T15:00:00Z","duration":90}""")

        for (duration in listOf(0, 481)) {
            mockMvc.perform(
                patch("/api/v1/trainings/$id")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"duration":$duration}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors.duration").value("must be between 1 and 480"))
        }
    }

    @Test
    fun `a teamId owned by another user is 400 unknown-team, never 404`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()
        val teamOfA = createTeam(tokenA)

        mockMvc.perform(
            post("/api/v1/trainings")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"teamId":"$teamOfA","day":"2024-10-24T15:00:00Z","duration":90}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/unknown-team"))
    }
}
