package com.coachplanner.api.config

import com.coachplanner.api.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** tasks.md T5 / AC INFRA-04: /actuator/health is public and shows the db component. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorHealthIT @Autowired constructor(private val mockMvc: MockMvc) {

    @Test
    fun `actuator health is reachable without authentication and reports status UP`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `actuator health includes a db component reported UP`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(jsonPath("$.components.db.status").value("UP"))
    }

    @Test
    fun `actuator info is reachable without authentication`() {
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isOk)
    }
}
