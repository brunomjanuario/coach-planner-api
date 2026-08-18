package com.coachplanner.api.reference

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/** tasks.md T43 / AC COMP-01, COMP-02, COMP-04, COMP-09. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CompetitionControllerIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val gameRepository: GameRepository,
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

    private fun createCompetition(token: String, name: String) = mockMvc.perform(
        post("/api/v1/competitions")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"$name"}"""),
    )

    @Test
    fun `a name with surrounding whitespace is stored and returned trimmed`() {
        val (token, _) = registerAndGetToken()

        createCompetition(token, "  Cup  ")
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.name").value("Cup"))
    }

    @Test
    fun `a whitespace-only name is rejected with 400`() {
        val (token, _) = registerAndGetToken()

        createCompetition(token, "   ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.name").value("must not be blank"))
    }

    @Test
    fun `GET competitions orders the list case-insensitively`() {
        val (token, _) = registerAndGetToken()
        createCompetition(token, "zebra").andExpect(status().isCreated)
        createCompetition(token, "Apple").andExpect(status().isCreated)
        createCompetition(token, "banana").andExpect(status().isCreated)

        val response = mockMvc.perform(get("/api/v1/competitions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val names = jsonMapper.readTree(response).toList().map { it.get("name").asString() }

        assert(names == listOf("Apple", "banana", "zebra")) { "expected case-insensitive ordering, got $names" }
    }

    @Test
    fun `DELETE removes the entry and leaves its name on historical games`() {
        val (token, ownerId) = registerAndGetToken()
        val response = createCompetition(token, "District League")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        val game = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, opponent = "Benfica", competition = "District League", date = Instant.now(), isHome = true),
        )

        mockMvc.perform(delete("/api/v1/competitions/$id").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent)

        val reloadedGame = gameRepository.findById(game.id).orElseThrow()
        assert(reloadedGame.competition == "District League") {
            "expected the game to keep its competition name after the entry was deleted, got ${reloadedGame.competition}"
        }

        val remaining = mockMvc.perform(get("/api/v1/competitions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assert(jsonMapper.readTree(remaining).toList().isEmpty()) { "expected the deleted entry to be gone from the list" }
    }
}
