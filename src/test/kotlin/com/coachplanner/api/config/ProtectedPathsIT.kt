package com.coachplanner.api.config

import com.coachplanner.api.TestcontainersConfiguration
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * tasks.md T18 / AC AUTH-09: every protected path rejects an unauthenticated
 * request with 401; every public path is reachable without a token (its own
 * validation/business logic may still reject the request for other reasons
 * — the point here is only that auth isn't what blocks it).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ProtectedPathsIT @Autowired constructor(private val mockMvc: MockMvc) {

    companion object {
        @JvmStatic
        fun protectedGetPaths() = listOf(
            "/__test/problems/not-found",
            "/__test/problems/validation",
            "/__test/problems/conflict",
            "/__test/whoami",
            "/api/v1/some-endpoint-that-does-not-exist-yet", // proves the default-deny rule, not just known paths
        )
    }

    @ParameterizedTest
    @MethodSource("protectedGetPaths")
    fun `every protected path returns 401 with no token`(path: String) {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized)
    }

    @ParameterizedTest
    @MethodSource("protectedGetPaths")
    fun `every protected path returns 401 with a garbage bearer token`(path: String) {
        mockMvc.perform(get(path).header("Authorization", "Bearer this-is-not-a-jwt")).andExpect(status().isUnauthorized)
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["/actuator/health", "/actuator/info"])
    fun `every public GET path succeeds with no token`(path: String) {
        mockMvc.perform(get(path)).andExpect(status().isOk)
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
        strings = ["/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"],
    )
    fun `every public auth POST path is reachable without a token — never a 401`(path: String) {
        // An empty body still fails validation (400), but that proves the
        // request reached the controller layer rather than being blocked
        // by authentication.
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect { result -> assert(result.response.status != 401) { "expected $path to not be blocked by auth, got 401" } }
    }
}
