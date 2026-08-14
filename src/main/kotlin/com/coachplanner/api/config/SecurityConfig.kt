package com.coachplanner.api.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

private const val BCRYPT_STRENGTH = 12

/**
 * PLACEHOLDER filter chain — T18 replaces it wholesale with the full JWT
 * resource-server chain per design.md (stateless, CORS, the complete public
 * path list, a parameterised test over every protected path). It exists now
 * only because spring-boot-starter-security has been on the classpath since
 * T1, so Spring Security secures every endpoint by default the moment a
 * datasource exists.
 *
 * /api/v1/auth/register is added here as the minimal necessary extension
 * for T14 (a new user isn't authenticated yet) — not the real allowlist.
 *
 * @ConditionalOnWebApplication is on filterChain specifically, not the
 * class: HttpSecurity is only available as a bean in a web application
 * context, but passwordEncoder() has no such dependency and AuthService
 * (a plain @Service, not web-conditional) needs it in every context,
 * including non-web test harnesses that boot the real application
 * (FlywayMigrationIT, FailFastIT — WebApplicationType.NONE). Guarding the
 * whole class here previously took passwordEncoder() down with it.
 */
@Configuration
class SecurityConfig {

    @Bean
    @ConditionalOnWebApplication
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/info", "/api/v1/auth/register").permitAll()
                    .anyRequest().authenticated()
            }
            .csrf { it.disable() }
        return http.build()
    }

    /** bcrypt cost 12 (AC AUTH-01) — used by AuthService to hash passwords on register/password-change. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)
}
