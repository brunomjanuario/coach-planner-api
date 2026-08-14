package com.coachplanner.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import javax.sql.DataSource
import kotlin.test.assertTrue

/**
 * AD-110 / tasks.md T1: proves the Boot 4.1 + Testcontainers v2 combination
 * boots and talks to real PostgreSQL before any domain code depends on it.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@DirtiesContext
class SmokeIT @Autowired constructor(private val dataSource: DataSource) {

    @Test
    fun `application context boots and holds a live connection to a real Postgres container`() {
        dataSource.connection.use { connection ->
            assertTrue(connection.isValid(5), "expected a live JDBC connection to the Testcontainers-provisioned Postgres instance")

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1").use { resultSet ->
                    assertTrue(resultSet.next(), "expected SELECT 1 to return a row")
                    assertTrue(resultSet.getInt(1) == 1, "expected SELECT 1 to return the value 1")
                }
            }
        }
    }
}
