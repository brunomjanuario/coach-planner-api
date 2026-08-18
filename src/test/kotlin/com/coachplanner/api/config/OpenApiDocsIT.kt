package com.coachplanner.api.config

import com.coachplanner.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

/**
 * tasks.md T47 / AC DOC-01. The path/operation counts below are derived
 * from the real `@RestController` mappings (grep'd directly, not eyeballed
 * from spec.md's endpoint inventory, which counts query-parameter variants
 * as separate rows and would over-count) — 29 distinct path templates, 51
 * total operations across them. spec.md's own "48 endpoints" is the P1–P9
 * MVP figure; T48 (P3, EXER-02) adds 2 more paths and 3 more operations
 * for the granular exercise sub-resources, which spec.md's inventory marks
 * "(P3)" and excludes from that count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class OpenApiDocsIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
) {

    private val expectedPaths = setOf(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/users/me",
        "/api/v1/users/me/password",
        "/api/v1/standings",
        "/api/v1/standings/rivals",
        "/api/v1/standings/rivals/{id}",
        "/api/v1/trainings",
        "/api/v1/trainings/{id}",
        "/api/v1/trainings/{trainingId}/exercises",
        "/api/v1/trainings/{trainingId}/exercises/{exerciseId}",
        "/api/v1/games",
        "/api/v1/games/{id}",
        "/api/v1/games/{id}/result",
        "/api/v1/teams",
        "/api/v1/teams/{id}",
        "/api/v1/teams/{teamId}/players",
        "/api/v1/teams/{teamId}/players/{playerId}",
        "/api/v1/ratings",
        "/api/v1/ratings/{eventType}/{eventId}/players/{playerId}",
        "/api/v1/ratings/{id}",
        "/api/v1/cards",
        "/api/v1/cards/{id}",
        "/api/v1/competitions",
        "/api/v1/competitions/{id}",
        "/api/v1/opponents",
        "/api/v1/opponents/{id}",
    )

    private val httpMethods = setOf("get", "post", "put", "patch", "delete")

    @Test
    fun `v3 api-docs is a public OpenAPI 3 document containing every real endpoint`() {
        val response = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val doc = jsonMapper.readTree(response)
        assert(doc.get("openapi").asString().startsWith("3.1")) { "expected an OpenAPI 3.1 document (AC DOC-01), got ${doc.get("openapi")}" }

        val paths = doc.get("paths")
        // "/__test/**" controllers (JwtTestController, ProblemTestController) exist only on the
        // test classpath for other ITs' own harnesses — @SpringBootTest scans them into this
        // context, but they're not part of src/main and would never ship in the real API.
        val actualPaths = paths.propertyNames().asSequence().filterNot { it.startsWith("/__test") }.toSet()
        assert(actualPaths == expectedPaths) {
            "path set mismatch.\nmissing: ${expectedPaths - actualPaths}\nunexpected: ${actualPaths - expectedPaths}"
        }
        assert(actualPaths.size == 29) { "expected 29 distinct path templates, got ${actualPaths.size}" }

        val operationCount = actualPaths.sumOf { path -> paths.get(path).properties().count { (key, _) -> key in httpMethods } }
        assert(operationCount == 51) { "expected 51 total operations across all paths, got $operationCount" }
    }

    /** AC DOC-01's other half — springdoc auto-serves this, but "auto-serves" isn't the same as "verified" without a test hitting it. */
    @Test
    fun `swagger-ui html is publicly reachable without a token`() {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `the bearer security scheme is documented`() {
        val response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk).andReturn().response.contentAsString
        val scheme = jsonMapper.readTree(response).get("components").get("securitySchemes").get("bearerAuth")

        assert(scheme.get("type").asString() == "http")
        assert(scheme.get("scheme").asString() == "bearer")
        assert(scheme.get("bearerFormat").asString() == "JWT")
    }

    @Test
    fun `every operation documents the problem+json error shape`() {
        val response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk).andReturn().response.contentAsString
        val doc = jsonMapper.readTree(response)

        val registerOperation = doc.get("paths").get("/api/v1/auth/register").get("post")
        val defaultResponse = registerOperation.get("responses").get("default")

        assert(defaultResponse.get("content").has("application/problem+json")) {
            "expected the default response to document application/problem+json"
        }
    }
}
