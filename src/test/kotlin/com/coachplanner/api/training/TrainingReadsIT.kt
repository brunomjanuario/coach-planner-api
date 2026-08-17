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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** tasks.md T29 / AC TRAIN-01…04. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TrainingReadsIT @Autowired constructor(
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

    private fun createTraining(token: String, teamId: String?, day: String): String {
        val teamField = if (teamId != null) "\"teamId\":\"$teamId\"," else ""
        val response = mockMvc.perform(
            post("/api/v1/trainings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{$teamField"day":"$day","duration":60}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("id").asString()
    }

    private fun getAll(token: String, query: String = ""): List<JsonNode> {
        val response = mockMvc.perform(get("/api/v1/trainings$query").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return jsonMapper.readTree(response).toList()
    }

    @Test
    fun `three trainings created out of date order return numbers 1,2,3 in date order`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)
        createTraining(token, teamId, "2024-10-24T15:00:00Z")
        createTraining(token, teamId, "2024-10-10T15:00:00Z")
        createTraining(token, teamId, "2024-10-17T15:00:00Z")

        val trainings = getAll(token)

        assert(trainings.size == 3) { "expected 3 trainings, got ${trainings.size}" }
        assert(trainings.map { it["day"].asString() } == listOf("2024-10-10T15:00:00Z", "2024-10-17T15:00:00Z", "2024-10-24T15:00:00Z"))
        assert(trainings.map { it["number"].asInt() } == listOf(1, 2, 3)) {
            "expected numbers 1,2,3 in date order, got ${trainings.map { it["number"] }}"
        }
    }

    @Test
    fun `filtering by teamId returns the same number values as the unfiltered call`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)
        val otherTeamId = createTeam(token)
        createTraining(token, teamId, "2024-10-24T15:00:00Z")
        createTraining(token, teamId, "2024-10-10T15:00:00Z")
        createTraining(token, otherTeamId, "2024-10-01T15:00:00Z")

        val unfiltered = getAll(token).filter { it["teamId"].asString() == teamId }.associate { it["day"].asString() to it["number"].asInt() }
        val filtered = getAll(token, "?teamId=$teamId").associate { it["day"].asString() to it["number"].asInt() }

        assert(filtered.size == 2) { "expected 2 trainings for teamId=$teamId, got ${filtered.size}" }
        assert(filtered == unfiltered) {
            "expected filtered numbers to match the unfiltered call's numbers for the same team: filtered=$filtered unfiltered=$unfiltered"
        }
        assert(filtered.values.toList() == listOf(1, 2)) { "expected numbers 1,2 for this team's own history, got ${filtered.values}" }
    }

    @Test
    fun `assigned=false returns only trainings whose teamId is null`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)
        createTraining(token, teamId, "2024-10-24T15:00:00Z")
        val unassignedId = createTraining(token, null, "2024-10-10T15:00:00Z")

        val unassigned = getAll(token, "?assigned=false")

        assert(unassigned.size == 1) { "expected 1 unassigned training, got ${unassigned.size}" }
        assert(unassigned[0]["id"].asString() == unassignedId)
        assert(unassigned[0]["teamId"].isNull)
        assert(unassigned[0]["number"].isNull) { "expected an unassigned training's number to be null" }
    }

    @Test
    fun `GET trainings by id returns exercises in stored order and the computed number`() {
        val token = registerAndGetToken()
        val teamId = createTeam(token)
        val response = mockMvc.perform(
            post("/api/v1/trainings")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"teamId":"$teamId","day":"2024-10-24T15:00:00Z","duration":60,
                       "exercises":[{"description":"Warm-up"},{"description":"Rondo"}]}""",
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        mockMvc.perform(get("/api/v1/trainings/$id").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.exercises.length()").value(2))
            .andExpect(jsonPath("$.exercises[0].description").value("Warm-up"))
            .andExpect(jsonPath("$.exercises[1].description").value("Rondo"))
    }
}
