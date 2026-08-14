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

/**
 * tasks.md T11 / AC ERR-01: a real controller throwing each ApiException
 * subtype returns the matching status and a stable RFC 9457 `type` URI.
 * Also T12 / AC ERR-02: bean-validation failures become a field-keyed
 * `errors` object. @WithMockUser bypasses SecurityConfig's placeholder
 * auth without carving a test-only exception into it.
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
}
