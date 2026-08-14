package com.coachplanner.api.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test-only DTO shaped like the wire format's null-vs-zero cases (design.md), not a real domain type. */
private data class SampleDto(
    val name: String,
    val playedAt: Instant?,
    val score: Int?,
)

/**
 * tasks.md T4: proves JacksonConfig's customizer actually took effect on
 * the auto-configured JsonMapper — @JsonTest boots only the JSON slice of
 * the context, so this exercises real Spring wiring, not a hand-built mapper.
 */
@JsonTest
class JsonMappingTest @Autowired constructor(private val jsonMapper: JsonMapper) {

    @Test
    fun `a null field survives serialization instead of being omitted`() {
        val dto = SampleDto(name = "friendly", playedAt = null, score = null)

        val json = jsonMapper.writeValueAsString(dto)

        assertTrue(json.contains("\"playedAt\":null"), "expected playedAt present as null, not omitted: $json")
        assertTrue(json.contains("\"score\":null"), "expected score present as null, not omitted: $json")
    }

    @Test
    fun `a nullable Instant round-trips as an ISO-8601 UTC string, not epoch millis`() {
        val instant = Instant.parse("2024-10-24T15:00:00Z")
        val dto = SampleDto(name = "match", playedAt = instant, score = 3)

        val json = jsonMapper.writeValueAsString(dto)
        assertTrue(
            json.contains("\"playedAt\":\"2024-10-24T15:00:00Z\""),
            "expected an ISO-8601 string, not a timestamp array/number: $json",
        )

        val roundTripped = jsonMapper.readValue(json, SampleDto::class.java)
        assertEquals(instant, roundTripped.playedAt)
    }

    @Test
    fun `an unknown incoming property is ignored, not rejected`() {
        val json = """{"name":"friendly","playedAt":null,"score":null,"somethingWeDontModel":"whatever"}"""

        val dto = jsonMapper.readValue(json, SampleDto::class.java)

        assertEquals("friendly", dto.name)
        assertNull(dto.score)
    }
}
