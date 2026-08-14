package com.coachplanner.api.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * tasks.md T5 / AC INFRA-05, INFRA-06: unit-tests StartupFailureLogger's
 * own behavior directly, with no Spring Boot application boot involved —
 * see FailFastIT for why a real end-to-end boot can't reliably capture this
 * log output (Boot's LoggingSystem reinitializes Logback mid-run, wiping
 * appenders attached before SpringApplication.run() is called).
 */
class StartupFailureLoggerTest {

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var logbackLogger: Logger

    @BeforeEach
    fun attachAppender() {
        appender = ListAppender()
        appender.start()
        logbackLogger = LoggerFactory.getLogger(StartupFailureLogger::class.java) as Logger
        logbackLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logbackLogger.detachAppender(appender)
    }

    @Test
    fun `logs the JDBC URL but never the password on a startup failure`() {
        val url = "jdbc:postgresql://db:5432/coachplanner"
        val secretPassword = "definitely-not-logged-1234"

        val environment = MockEnvironment()
            .withProperty("spring.datasource.url", url)
            .withProperty("spring.datasource.password", secretPassword)
        val context = mock(ConfigurableApplicationContext::class.java)
        `when`(context.environment).thenReturn(environment)
        val event = ApplicationFailedEvent(SpringApplication(), emptyArray(), context, RuntimeException("boom"))

        StartupFailureLogger().onApplicationEvent(event)

        val logged = appender.list.joinToString("\n") { it.formattedMessage }
        assertTrue(logged.contains(url), "expected the JDBC URL in the failure log: $logged")
        assertFalse(logged.contains(secretPassword), "the password must never appear in logs: $logged")
    }

    @Test
    fun `does nothing when the failed context has no datasource url configured`() {
        val context = mock(ConfigurableApplicationContext::class.java)
        `when`(context.environment).thenReturn(MockEnvironment())
        val event = ApplicationFailedEvent(SpringApplication(), emptyArray(), context, RuntimeException("boom"))

        StartupFailureLogger().onApplicationEvent(event)

        assertTrue(appender.list.isEmpty(), "expected no log line when there is no datasource url to report")
    }
}
