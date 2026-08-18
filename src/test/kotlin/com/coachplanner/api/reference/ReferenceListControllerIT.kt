package com.coachplanner.api.reference

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/** One entity per line — the label is what shows up in the test report. */
class ReferenceListCase(
    val label: String,
    val basePath: String,
    val gameWithName: (ownerId: UUID, name: String) -> Game,
    val nameOnGame: (Game) -> String?,
) {
    override fun toString() = label
}

/**
 * tasks.md T43 / AC COMP-01, COMP-02, COMP-04, COMP-09 and T44 / AC OPP-01…04
 * — "the T43 suite re-run against `/opponents`, parameterised over both
 * endpoints rather than copied." One suite, two cases: competitions and
 * opponents differ only in which `games` column carries the historical name.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReferenceListControllerIT @Autowired constructor(
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

    private fun create(token: String, basePath: String, name: String): ResultActions = mockMvc.perform(
        post(basePath)
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"$name"}"""),
    )

    private fun getAll(token: String, basePath: String) = mockMvc.perform(get(basePath).header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk)
        .andReturn().response.contentAsString

    fun cases(): List<ReferenceListCase> = listOf(
        ReferenceListCase(
            "Competition",
            "/api/v1/competitions",
            gameWithName = { owner, name ->
                Game(ownerId = owner, opponent = "Benfica", competition = name, date = Instant.now(), isHome = true)
            },
            nameOnGame = { it.competition },
        ),
        ReferenceListCase(
            "Opponent",
            "/api/v1/opponents",
            gameWithName = { owner, name -> Game(ownerId = owner, opponent = name, date = Instant.now(), isHome = true) },
            nameOnGame = { it.opponent },
        ),
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `a name with surrounding whitespace is stored and returned trimmed`(case: ReferenceListCase) {
        val (token, _) = registerAndGetToken()

        create(token, case.basePath, "  Cup  ")
            .andExpect(status().isCreated)
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.name").value("Cup"))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `a whitespace-only name is rejected with 400`(case: ReferenceListCase) {
        val (token, _) = registerAndGetToken()

        create(token, case.basePath, "   ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.name").value("must not be blank"))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `GET orders the list case-insensitively`(case: ReferenceListCase) {
        val (token, _) = registerAndGetToken()
        create(token, case.basePath, "zebra").andExpect(status().isCreated)
        create(token, case.basePath, "Apple").andExpect(status().isCreated)
        create(token, case.basePath, "banana").andExpect(status().isCreated)

        val names = jsonMapper.readTree(getAll(token, case.basePath)).toList().map { it.get("name").asString() }

        assert(names == listOf("Apple", "banana", "zebra")) { "expected case-insensitive ordering, got $names" }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `DELETE removes the entry and leaves its name on historical games`(case: ReferenceListCase) {
        val (token, ownerId) = registerAndGetToken()
        val response = create(token, case.basePath, "District League")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        val game = gameRepository.saveAndFlush(case.gameWithName(ownerId, "District League"))

        mockMvc.perform(delete("${case.basePath}/$id").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent)

        val reloadedGame = gameRepository.findById(game.id).orElseThrow()
        assert(case.nameOnGame(reloadedGame) == "District League") {
            "expected the game to keep its name after the entry was deleted, got ${case.nameOnGame(reloadedGame)}"
        }

        val remaining = jsonMapper.readTree(getAll(token, case.basePath)).toList()
        assert(remaining.isEmpty()) { "expected the deleted entry to be gone from the list" }
    }

    /** tasks.md T45 / AC COMP-05/06. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `renaming an entry cascades to every one of the caller's games carrying the old name`(case: ReferenceListCase) {
        val (token, ownerId) = registerAndGetToken()
        val response = create(token, case.basePath, "District League")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        val gameA = gameRepository.saveAndFlush(case.gameWithName(ownerId, "district league"))
        val gameB = gameRepository.saveAndFlush(case.gameWithName(ownerId, "DISTRICT LEAGUE"))

        mockMvc.perform(
            patch("${case.basePath}/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Premier League"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Premier League"))

        val reloadedA = gameRepository.findById(gameA.id).orElseThrow()
        val reloadedB = gameRepository.findById(gameB.id).orElseThrow()
        assert(case.nameOnGame(reloadedA) == "Premier League") { "expected gameA's name to cascade, got ${case.nameOnGame(reloadedA)}" }
        assert(case.nameOnGame(reloadedB) == "Premier League") { "expected gameB's name to cascade, got ${case.nameOnGame(reloadedB)}" }
    }

    /** tasks.md T45 / AC COMP-08. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `a no-op rename to the same trimmed name performs no cascade and returns 200`(case: ReferenceListCase) {
        val (token, ownerId) = registerAndGetToken()
        val response = create(token, case.basePath, "District League")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        val game = gameRepository.saveAndFlush(case.gameWithName(ownerId, "District League"))

        mockMvc.perform(
            patch("${case.basePath}/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"  District League  "}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("District League"))

        val reloadedGame = gameRepository.findById(game.id).orElseThrow()
        assert(case.nameOnGame(reloadedGame) == "District League") { "expected the game's name to be untouched by a no-op rename" }
    }
}
