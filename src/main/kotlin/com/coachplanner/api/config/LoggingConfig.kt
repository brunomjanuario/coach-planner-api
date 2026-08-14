package com.coachplanner.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val CORRELATION_ID_HEADER = "X-Request-Id"
private const val MDC_KEY = "requestId"

/**
 * One log line per request carrying a correlation id, so a single request's
 * log lines can be grepped together. Boot's built-in structured JSON logging
 * (logging.structured.format.console=ecs, application.yml) includes MDC
 * context automatically — this filter's only job is to populate and clear
 * it. Reuses an incoming X-Request-Id if the caller already has one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(CorrelationIdFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = request.getHeader(CORRELATION_ID_HEADER)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, correlationId)
        response.setHeader(CORRELATION_ID_HEADER, correlationId)
        val start = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - start
            log.info("{} {} -> {} ({} ms)", request.method, request.requestURI, response.status, durationMs)
            MDC.remove(MDC_KEY)
        }
    }
}

/**
 * Fails fast on its own — Flyway/Hikari refuse to proceed without a DB
 * connection, so a bad datasource never reaches a "started but degraded"
 * state. This listener doesn't change that; it guarantees the JDBC URL is
 * logged and the password never is (AC INFRA-05/06), regardless of how
 * Hikari/Flyway's own failure message happens to be worded.
 *
 * Registered via META-INF/spring.factories, not @Component: the failure
 * this must catch happens during the very first bean creation phase
 * (entityManagerFactory -> flywayInitializer), before context-registered
 * @Component ApplicationListener beans are reliably wired into the event
 * multicaster. Bootstrap-time listeners are registered before context
 * refresh starts, so they fire regardless of how early it fails.
 */
class StartupFailureLogger : ApplicationListener<ApplicationFailedEvent> {

    private val log = LoggerFactory.getLogger(StartupFailureLogger::class.java)

    override fun onApplicationEvent(event: ApplicationFailedEvent) {
        val url = event.applicationContext?.environment?.getProperty("spring.datasource.url") ?: return
        log.error("Application failed to start. Database JDBC URL: {}", url)
    }
}
