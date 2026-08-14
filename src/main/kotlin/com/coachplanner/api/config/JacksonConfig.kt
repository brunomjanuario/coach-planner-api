package com.coachplanner.api.config

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper

/**
 * Pins Jackson 3 behaviour explicitly rather than relying on whatever Boot
 * 4.1 currently defaults to (spring.jackson.use-jackson2-defaults=false is
 * itself a new default in this line — AD-101). The Kotlin module needs no
 * registration here: jackson-module-kotlin self-registers via
 * META-INF/services/tools.jackson.databind.JacksonModule, and
 * spring.jackson.find-and-add-modules (default true) picks it up.
 */
@Component
class JacksonConfig : JsonMapperBuilderCustomizer {

    override fun customize(builder: JsonMapper.Builder) {
        builder
            // Nulls must survive serialization — e.g. Game.usScore: null is
            // how the frontend's hasResult() distinguishes "not played yet"
            // from a recorded 0-0 (the null-vs-zero trap, design.md).
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.ALWAYS) }
            // A request body carrying a field this API doesn't model is
            // ignored, not rejected (spec.md edge case).
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Instants serialize as ISO-8601 strings ("2024-10-24T15:00:00Z"),
            // never epoch-millisecond timestamps (design.md wire format).
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
