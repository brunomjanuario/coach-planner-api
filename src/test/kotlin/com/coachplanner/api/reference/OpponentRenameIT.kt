package com.coachplanner.api.reference

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.common.newId
import com.coachplanner.api.standings.RivalRow
import com.coachplanner.api.standings.RivalRowRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** tasks.md T45 / AC COMP-06: renaming an opponent must not touch a rival standings row sharing the name — a separate model entirely (frontend AD-010). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class OpponentRenameIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val rivalRowRepository: RivalRowRepository,
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
    fun `renaming an opponent does not touch a rival standings row sharing that name`() {
        val (token, ownerId) = registerAndGetToken()
        val bearer = "Bearer $token"

        val response = mockMvc.perform(
            post("/api/v1/opponents")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Benfica"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val id = jsonMapper.readTree(response).get("id").asString()

        val rivalRow = rivalRowRepository.saveAndFlush(
            RivalRow(ownerId = ownerId, name = "Benfica", played = 3, won = 2, drawn = 1, lost = 0, goalsFor = 6, goalsAgainst = 2),
        )

        mockMvc.perform(
            patch("/api/v1/opponents/$id")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"S.L. Benfica"}"""),
        ).andExpect(status().isOk)

        val reloadedRow = rivalRowRepository.findById(rivalRow.id).orElseThrow()
        assert(reloadedRow.name == "Benfica") { "expected the rival row's name to be untouched by the opponent rename, got ${reloadedRow.name}" }
    }
}
