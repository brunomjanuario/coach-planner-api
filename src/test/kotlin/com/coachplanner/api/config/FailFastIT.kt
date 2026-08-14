package com.coachplanner.api.config

import com.coachplanner.api.CoachPlannerApiApplication
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import kotlin.test.assertTrue

/**
 * tasks.md T5 / AC INFRA-06: an unreachable database fails startup instead
 * of starting degraded. The log-content assertion (URL logged, password
 * never logged) lives in StartupFailureLoggerTest, unit-tested in isolation
 * — Boot's LoggingSystem reinitializes Logback as part of every
 * SpringApplication.run() (during environment preparation), which detaches
 * any appender attached before that call, so a real end-to-end boot cannot
 * reliably capture its own failure-path log output.
 */
class FailFastIT {

    @Test
    fun `an unreachable database fails the application at startup rather than starting degraded`() {
        var startupFailed = false
        try {
            SpringApplicationBuilder(CoachPlannerApiApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties(
                    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/nonexistent",
                    "spring.datasource.username=nope",
                    "spring.datasource.password=irrelevant",
                    "spring.datasource.hikari.initialization-fail-timeout=1",
                    "spring.datasource.hikari.connection-timeout=1000",
                )
                .run()
                .close()
        } catch (_: Exception) {
            startupFailed = true
        }

        assertTrue(startupFailed, "expected startup to fail fast against an unreachable database, not start degraded")
    }
}
