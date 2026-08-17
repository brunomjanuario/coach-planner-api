package com.coachplanner.api.training

import com.coachplanner.api.common.ValidationException
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** Mirrors the frontend's `SHAPE_KINDS` (`lib/exerciseDiagram.js`) exactly — the eight kinds it can ever emit. */
private val SHAPE_KINDS = setOf("player-a", "player-b", "cone", "ball", "goal", "line", "arrow", "text")

/** Mirrors the frontend's `LIMITS` (design.md, AC TRAIN-09). */
private const val MAX_SHAPES = 60
private const val MAX_BYTES = 8192

/**
 * Enforces the frontend's diagram rules (AD-102, frontend AD-015) at the one
 * point every diagram passes through on its way into `jsonb`: create and
 * update both funnel through `TrainingService.replaceExercises`.
 *
 * Shape count and an unrecognised `kind` are rejected with 400 — a
 * corrupted or hostile payload. Out-of-bounds coordinates are **clamped**,
 * not rejected, mirroring the frontend's `clampToPitch`: they are the
 * ordinary result of a drag that overshoots the pitch edge, not an error.
 */
@Component
class DiagramValidator(private val jsonMapper: JsonMapper) {

    fun validate(diagram: Map<String, Any?>?): Map<String, Any?>? {
        if (diagram == null) return null

        val shapes = diagram["shapes"] as? List<*> ?: emptyList<Any?>()
        if (shapes.size > MAX_SHAPES) {
            throw ValidationException("A diagram cannot have more than $MAX_SHAPES shapes.", "diagram-invalid")
        }

        val clampedShapes = shapes.map { clampShape(it) }
        val clamped = diagram + ("shapes" to clampedShapes)

        val bytes = jsonMapper.writeValueAsBytes(clamped).size
        if (bytes > MAX_BYTES) {
            throw ValidationException("A diagram cannot exceed $MAX_BYTES bytes when saved.", "diagram-invalid")
        }

        return clamped
    }

    private fun clampShape(rawShape: Any?): Map<String, Any?> {
        val shape = rawShape as? Map<*, *> ?: throw ValidationException("A diagram shape is invalid.", "diagram-invalid")
        val kind = shape["kind"] as? String
        if (kind == null || kind !in SHAPE_KINDS) {
            throw ValidationException("Unknown diagram shape kind: $kind", "diagram-invalid")
        }

        val result = LinkedHashMap<String, Any?>()
        shape.forEach { (key, value) -> result[key.toString()] = value }

        val points = result["points"] as? List<*>
        if (points != null) {
            result["points"] = points.map { rawPoint ->
                val point = rawPoint as? List<*> ?: emptyList<Any?>()
                listOf(clamp01(point.getOrNull(0)), clamp01(point.getOrNull(1)))
            }
        } else {
            if (result.containsKey("x")) result["x"] = clamp01(result["x"])
            if (result.containsKey("y")) result["y"] = clamp01(result["y"])
        }

        return result
    }

    /** Mirrors the frontend's `clamp01`: a non-finite value clamps to 0, otherwise clamps into `0..1`. */
    private fun clamp01(value: Any?): Double {
        val n = (value as? Number)?.toDouble() ?: return 0.0
        if (!n.isFinite()) return 0.0
        return n.coerceIn(0.0, 1.0)
    }
}
