package com.coachplanner.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.json.JsonMapper
import javax.crypto.spec.SecretKeySpec

private const val BCRYPT_STRENGTH = 12
private const val HMAC_ALGORITHM = "HmacSHA256"
private const val VITE_DEV_ORIGIN = "http://localhost:5173"

/**
 * The real resource-server chain (AC AUTH-09, AUTH-10, design.md). Stateless
 * — every request authenticates itself via its bearer token, no session.
 * Public: the actuator paths T5 needed, and the three auth endpoints that
 * necessarily precede having a token (register/login/refresh). Everything
 * else under the /api/v1 prefix requires a valid, unexpired, correctly-signed JWT.
 * A ProblemJsonAuthenticationEntryPoint replaces Spring Security's default
 * 401 (WWW-Authenticate header only) with the same RFC 9457 shape every
 * other error in this API uses (AD-109), and distinguishes an expired
 * token from any other failure.
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
    fun filterChain(http: HttpSecurity, jwtDecoder: JwtDecoder, jsonMapper: JsonMapper): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/actuator/health", "/actuator/info",
                    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
                ).permitAll()
                    .anyRequest().authenticated()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .oauth2ResourceServer {
                // OAuth2ResourceServerConfigurer registers its own entry point for bearer-token
                // requests (a DelegatingAuthenticationEntryPoint keyed by request matcher), which
                // overrides a generic .exceptionHandling { it.authenticationEntryPoint(...) } —
                // it has to be set here specifically, verified by the 401 body actually being empty
                // until this moved from .exceptionHandling to this exact hook.
                it.authenticationEntryPoint(ProblemJsonAuthenticationEntryPoint(jsonMapper))
                it.jwt { jwt -> jwt.decoder(jwtDecoder) }
            }
            .csrf { it.disable() }
        return http.build()
    }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(VITE_DEV_ORIGIN)
            allowedMethods = listOf("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
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
