package com.coachplanner.api.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * PLACEHOLDER — Phase 3 (tasks.md T14-T21) replaces this with the full JWT
 * resource-server chain per design.md. It exists now only because
 * spring-boot-starter-security is already on the classpath (T1), so Spring
 * Security secures every endpoint by default the moment a datasource
 * exists — including /actuator/info, which (unlike /actuator/health) has no
 * built-in permit from Boot's own security autoconfiguration. Without this,
 * T5's own AC INFRA-04 (info reachable) cannot pass.
 *
 * Deliberately narrow: permits only the two actuator endpoints this task
 * exposes; everything else stays authenticated (denied, until Phase 3 adds
 * real auth) rather than opened up.
 *
 * @ConditionalOnWebApplication: HttpSecurity is only available as a bean in
 * a web application context. Without this, non-web test harnesses that boot
 * the real application (FlywayMigrationIT, FailFastIT — WebApplicationType.NONE)
 * fail with an unrelated NoSuchBeanDefinitionException before ever reaching
 * the behavior they're actually testing.
 */
@Configuration
@ConditionalOnWebApplication
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { it.requestMatchers("/actuator/health", "/actuator/info").permitAll().anyRequest().authenticated() }
            .csrf { it.disable() }
        return http.build()
    }
}
