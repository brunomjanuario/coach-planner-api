package com.coachplanner.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import kotlin.test.assertEquals

/** tasks.md T3: ddl-auto must be "validate" — Flyway owns the schema, never Hibernate auto-DDL. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class ConfigPropertiesIT @Autowired constructor(private val environment: Environment) {

    @Test
    fun `hibernate ddl-auto is validate`() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"))
    }
}
