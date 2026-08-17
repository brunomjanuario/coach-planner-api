package com.coachplanner.api.training.dto

import com.coachplanner.api.training.Exercise
import java.util.UUID

/**
 * `order_index` is deliberately not serialized (design.md): exercise order
 * is carried by array position alone, which is what the frontend already
 * relies on. `duration` is the wire name for `duration_minutes`.
 */
data class ExerciseDto(
    val id: UUID,
    val trainingId: UUID,
    val description: String,
    val numberOfPlayers: Int?,
    val duration: Int?,
    val repetitions: Int?,
    val diagram: Map<String, Any?>?,
) {
    companion object {
        fun from(exercise: Exercise) = ExerciseDto(
            id = exercise.id,
            trainingId = exercise.training.id,
            description = exercise.description,
            numberOfPlayers = exercise.numberOfPlayers,
            duration = exercise.durationMinutes,
            repetitions = exercise.repetitions,
            diagram = exercise.diagram,
        )
    }
}
