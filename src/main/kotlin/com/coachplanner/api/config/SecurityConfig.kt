package com.coachplanner.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.spec.SecretKeySpec

private const val BCRYPT_STRENGTH = 12
private const val HMAC_ALGORITHM = "HmacSHA256"

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
 * T15 adds JWT verification (a JwtDecoder bean, wired via .oauth2ResourceServer)
 * so bearer tokens are actually checked on every other path; T18 still
 * owns the complete public-path list, CORS, and statelessness.
 *
 * @ConditionalOnWebApplication is on filterChain specifically, not the
 * class: HttpSecurity is only available as a bean in a web application
 * context, but passwordEncoder()/jwtDecoder() have no such dependency and
 * AuthService/JwtService (plain @Service beans, not web-conditional) need
 * them in every context, including non-web test harnesses that boot the
 * real application (FlywayMigrationIT, FailFastIT — WebApplicationType.NONE).
 * Guarding the whole class here previously took passwordEncoder() down
 * with it (T14's lesson).
 */
@Configuration
class SecurityConfig {

    @Bean
    @ConditionalOnWebApplication
    fun filterChain(http: HttpSecurity, jwtDecoder: JwtDecoder): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/actuator/health", "/actuator/info",
                    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.decoder(jwtDecoder) } }
            .csrf { it.disable() }
        return http.build()
    }

    /** bcrypt cost 12 (AC AUTH-01) — used by AuthService to hash passwords on register/password-change. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    /** Verifies the same HS256-signed tokens JwtService issues — the shared app.jwt.secret is the trust anchor. */
    @Bean
    fun jwtDecoder(@Value("\${app.jwt.secret}") secret: String): JwtDecoder {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
    }
}
