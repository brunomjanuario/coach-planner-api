package com.coachplanner.api.common

import com.coachplanner.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFalse

/**
 * tasks.md T11 / AC ERR-01: a real controller throwing each ApiException
 * subtype returns the matching status and a stable RFC 9457 `type` URI.
 * Also T12 / AC ERR-02: bean-validation failures become a field-keyed
 * `errors` object. Also T13 / AC ERR-03…06: infrastructure failure mapping
 * — 503 for an unavailable DB, 409 for a stale write, 400 for an
 * unparseable path variable, and a 500 that leaks nothing. @WithMockUser
 * bypasses SecurityConfig's placeholder auth without carving a test-only
 * exception into it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@WithMockUser
class ApiExceptionHandlerIT @Autowired constructor(private val mockMvc: MockMvc) {

    @Test
    fun `NotFoundException becomes a 404 problem+json with the not-found type`() {
        mockMvc.perform(get("/__test/problems/not-found"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/not-found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("nothing here"))
    }

    @Test
    fun `ValidationException becomes a 400 problem+json with the validation-failed type`() {
        mockMvc.perform(get("/__test/problems/validation"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/validation-failed"))
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `ConflictException becomes a 409 problem+json with the conflict type`() {
        mockMvc.perform(get("/__test/problems/conflict"))
            .andExpect(status().isConflict)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/conflict"))
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `a request body failing two bean-validation constraints yields both field keys with readable messages`() {
        mockMvc.perform(
            post("/__test/problems/bean-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "", "age": 0}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/validation-failed"))
            .andExpect(jsonPath("$.errors.name").value("must not be blank"))
            .andExpect(jsonPath("$.errors.age").value("must be at least 1"))
    }

    @Test
    fun `DataAccessResourceFailureException becomes a 503 database-unavailable, not a 500`() {
        mockMvc.perform(get("/__test/problems/db-unavailable"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/database-unavailable"))
            .andExpect(jsonPath("$.status").value(503))
    }

    @Test
    fun `OptimisticLockingFailureException becomes a 409 stale-version`() {
        mockMvc.perform(get("/__test/problems/stale-version"))
            .andExpect(status().isConflict)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/stale-version"))
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun `an unparseable UUID path variable is a 400, not a 500`() {
        mockMvc.perform(get("/__test/problems/by-id/not-a-real-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/malformed-parameter"))
    }

    @Test
    fun `a valid UUID path variable reaches the controller normally`() {
        val id = "018f1e2d-0000-7000-8000-000000000001"
        mockMvc.perform(get("/__test/problems/by-id/$id"))
            .andExpect(status().isOk)
    }

    @Test
    fun `an unhandled exception becomes a generic 500 that leaks no exception detail`() {
        val result = mockMvc.perform(get("/__test/problems/unexpected"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coachplanner.dev/problems/internal-error"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
            .andReturn()

        val body = result.response.contentAsString
        assertFalse(body.contains("RuntimeException"), "response must not name the exception class: $body")
        assertFalse(body.contains("SELECT"), "response must not leak SQL: $body")
        assertFalse(body.contains("password_hash"), "response must not leak the exception message: $body")
    }
}
