package com.coachplanner.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource
import kotlin.test.assertEquals

/**
 * tasks.md T3: proves the Flyway pipeline itself, not just that it's wired
 * up — apply on a fresh database, then a second independent application
 * boot against the SAME database must be a no-op, not a re-apply or error.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
    }

    private fun boot(): ConfigurableApplicationContext =
        SpringApplicationBuilder(CoachPlannerApiApplication::class.java)
            .web(WebApplicationType.NONE)
            .properties(
                "spring.datasource.url=${postgres.jdbcUrl}",
                "spring.datasource.username=${postgres.username}",
                "spring.datasource.password=${postgres.password}",
            )
            .run()

    private fun successfulMigrationCount(context: ConfigurableApplicationContext): Int {
        val jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
        return jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success = true",
            Int::class.java,
        )!!
    }

    @Test
    fun `V1 applies on first boot and a second independent boot against the same database is a no-op`() {
        val first = boot()
        try {
            assertEquals(
                1,
                successfulMigrationCount(first),
                "expected exactly one successfully-applied migration after the first boot",
            )
        } finally {
            first.close()
        }

        val second = boot()
        try {
            assertEquals(
                1,
                successfulMigrationCount(second),
                "a second boot against the already-migrated database must not re-apply or duplicate the migration",
            )
        } finally {
            second.close()
        }
    }
}
