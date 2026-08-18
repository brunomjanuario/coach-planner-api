package com.coachplanner.api.reference

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * tasks.md T45 / AC COMP-07: a forced mid-cascade failure must roll back the
 * rename entirely — both the reference entry's own new name and every game
 * already flushed before the failure. Only a genuinely transactional
 * implementation passes this: a "rename first, cascade after in separate
 * writes" implementation would leave the entry renamed while some games
 * still carried the old name — this test is what tells the two apart.
 *
 * `@MockitoSpyBean` replaces `GameRepository` everywhere in the context
 * (including inside `ReferenceListConfig`'s cascade lambda) with a spy
 * wrapping the real bean, so the initial game-seeding calls below run for
 * real and only the stubbed call (set up afterwards) forces a failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ReferenceListRenameRollbackIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
) {

    @MockitoSpyBean
    private lateinit var gameRepository: GameRepository

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
    fun `a forced mid-cascade failure rolls back the rename entirely`() {
        val (token, ownerId) = registerAndGetToken()
        val bearer = "Bearer $token"

        val response = mockMvc.perform(
            post("/api/v1/competitions")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"District League"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        val gameA = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, opponent = "Benfica", competition = "District League", date = Instant.now(), isHome = true),
        )
        val gameB = gameRepository.saveAndFlush(
            Game(ownerId = ownerId, opponent = "Sporting", competition = "District League", date = Instant.now(), isHome = false),
        )

        // Only the cascade's own two calls are counted — this stub is installed after the seeding saves above.
        val callCount = AtomicInteger(0)
        doAnswer { invocation ->
            if (callCount.incrementAndGet() == 2) {
                throw RuntimeException("forced mid-cascade failure")
            }
            invocation.callRealMethod()
        }.`when`(gameRepository).saveAndFlush(any())

        mockMvc.perform(
            patch("/api/v1/competitions/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Premier League"}"""),
        ).andExpect(status().isInternalServerError)

        val reloadedA = gameRepository.findById(gameA.id).orElseThrow()
        val reloadedB = gameRepository.findById(gameB.id).orElseThrow()
        assert(reloadedA.competition == "District League") { "expected gameA's rename to be rolled back, got ${reloadedA.competition}" }
        assert(reloadedB.competition == "District League") { "expected gameB's rename to be rolled back, got ${reloadedB.competition}" }

        val entriesResponse = mockMvc.perform(get("/api/v1/competitions").header(HttpHeaders.AUTHORIZATION, bearer))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val names = jsonMapper.readTree(entriesResponse).toList().map { it.get("name").asString() }
        assert(names == listOf("District League")) { "expected the competition entry's own rename to be rolled back too, got $names" }
    }
}
