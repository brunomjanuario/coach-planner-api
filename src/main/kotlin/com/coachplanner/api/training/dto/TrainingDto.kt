package com.coachplanner.api.training.dto

import com.coachplanner.api.training.Training
import java.time.Instant
import java.util.UUID

/** `number` is derived on read (AD-108) and is null for an unassigned training. */
data class TrainingDto(
    val id: UUID,
    val teamId: UUID?,
    val number: Int?,
    val day: Instant,
    val duration: Int,
    val exercises: List<ExerciseDto>,
) {
    companion object {
        fun from(training: Training, number: Int?) = TrainingDto(
            id = training.id,
            teamId = training.teamId,
            number = number,
            day = training.day,
            duration = training.durationMinutes,
            exercises = training.exercises.map { ExerciseDto.from(it) },
        )
    }
}
