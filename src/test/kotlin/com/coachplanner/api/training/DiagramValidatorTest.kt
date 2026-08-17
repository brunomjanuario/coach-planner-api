package com.coachplanner.api.training

import com.coachplanner.api.common.ValidationException
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** tasks.md T30 / AC TRAIN-09, TRAIN-10. Pure logic — no Spring context. */
class DiagramValidatorTest {

    private val validator = DiagramValidator(JsonMapper.builder().build())

    private fun shape(kind: String = "cone", x: Any? = 0.5, y: Any? = 0.5) =
        mapOf("id" to "s1", "kind" to kind, "x" to x, "y" to y)

    @Test
    fun `a diagram with 61 shapes is rejected`() {
        val diagram = mapOf("v" to 1, "pitch" to "full", "shapes" to List(61) { shape() })

        assertFailsWith<ValidationException> { validator.validate(diagram) }
    }

    @Test
    fun `a diagram with exactly 60 shapes is accepted`() {
        val diagram = mapOf("v" to 1, "pitch" to "full", "shapes" to List(60) { shape() })

        val result = validator.validate(diagram)

        assertEquals(60, (result?.get("shapes") as List<*>).size)
    }

    @Test
    fun `a diagram over 8192 serialized bytes is rejected even with a single shape`() {
        val hugeShape = mapOf("id" to "s1", "kind" to "text", "x" to 0.5, "y" to 0.5, "text" to "a".repeat(9000))
        val diagram = mapOf("v" to 1, "pitch" to "full", "shapes" to listOf(hugeShape))

        assertFailsWith<ValidationException> { validator.validate(diagram) }
    }

    @Test
    fun `an unrecognised shape kind is rejected`() {
        val diagram = mapOf("v" to 1, "pitch" to "full", "shapes" to listOf(shape(kind = "spaceship")))

        assertFailsWith<ValidationException> { validator.validate(diagram) }
    }

    @Test
    fun `a point shape's x coordinate of 1_7 is clamped to 1_0, not rejected`() {
        val diagram = mapOf("v" to 1, "pitch" to "full", "shapes" to listOf(shape(x = 1.7)))

        val result = validator.validate(diagram)

        val resultShape = (result?.get("shapes") as List<*>)[0] as Map<*, *>
        assertEquals(1.0, resultShape["x"])
    }

    @Test
    fun `a path shape's out-of-bounds points are clamped into the pitch bounds`() {
        val pathShape = mapOf("id" to "s1", "kind" to "line", "points" to listOf(listOf(-0.3, 1.7), listOf(0.5, 0.5)))
        val diagram = mapOf("v" to 1, "pitch" to "full", "shapes" to listOf(pathShape))

        val result = validator.validate(diagram)

        val resultShape = (result?.get("shapes") as List<*>)[0] as Map<*, *>
        assertEquals(listOf(listOf(0.0, 1.0), listOf(0.5, 0.5)), resultShape["points"])
    }

    @Test
    fun `diagram null round-trips as null, never an empty object`() {
        assertNull(validator.validate(null))
    }
}
