package com.coachplanner.api.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * springdoc auto-discovers every `@RestController` mapping (AC DOC-01) — it
 * needs no per-endpoint annotations to document them. This config supplies
 * the two things it can't infer on its own: the bearer-token scheme every
 * protected endpoint actually requires, and the RFC 9457 problem+json shape
 * every error response actually uses (AD-109, `ApiExceptionHandler`), which
 * no controller method declares via `@ApiResponse` — annotating every
 * controller individually would duplicate the error shape once per
 * endpoint, exactly the AD-104-style duplication this project avoids
 * elsewhere.
 */
@Configuration
@OpenAPIDefinition(info = Info(title = "Coach Planner API", version = "v1"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    `in` = SecuritySchemeIn.HEADER,
)
class OpenApiConfig {

    /** Applied to the document as a whole — the three endpoints that precede having a token are a well-known, expected exception in any bearer-secured API's docs. */
    @Bean
    fun openApiSecurity(): OpenAPI = OpenAPI().addSecurityItem(SecurityRequirement().addList("bearerAuth"))

    /** One customizer, applied to every operation — not one `@ApiResponse` per controller method. */
    @Bean
    fun problemJsonOperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, _ ->
            val schema = Schema<Any>().apply {
                type = "object"
                title = "ProblemDetail"
                addProperty("type", Schema<String>().type("string"))
                addProperty("title", Schema<String>().type("string"))
                addProperty("status", Schema<Int>().type("integer"))
                addProperty("detail", Schema<String>().type("string"))
                addProperty("instance", Schema<String>().type("string"))
            }
            val content = Content().addMediaType("application/problem+json", MediaType().schema(schema))
            operation.responses.addApiResponse(
                "default",
                ApiResponse().description("Error (RFC 9457 problem+json)").content(content),
            )
            operation
        }
}
