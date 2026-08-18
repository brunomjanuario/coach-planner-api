package com.coachplanner.api.training

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.training.dto.ExerciseDto
import com.coachplanner.api.training.dto.ExerciseRequest
import com.coachplanner.api.training.dto.UpdateExerciseRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/** Granular exercise sub-resources (P3, AC EXER-02) — the frontend submits whole trainings today; these have no caller yet. */
@RestController
@RequestMapping("/api/v1/trainings/{trainingId}/exercises")
class ExerciseController(private val trainingService: TrainingService) {

    @PostMapping
    fun create(
        @PathVariable trainingId: UUID,
        @CurrentUser ownerId: UUID,
        @Valid @RequestBody request: ExerciseRequest,
    ): ResponseEntity<ExerciseDto> {
        val created = trainingService.addExercise(trainingId, ownerId, request)
        return ResponseEntity.created(URI.create("/api/v1/trainings/$trainingId/exercises/${created.id}")).body(created)
    }

    @PatchMapping("/{exerciseId}")
    fun update(
        @PathVariable trainingId: UUID,
        @PathVariable exerciseId: UUID,
        @CurrentUser ownerId: UUID,
        @Valid @RequestBody request: UpdateExerciseRequest,
    ): ExerciseDto = trainingService.updateExercise(trainingId, exerciseId, ownerId, request)

    @DeleteMapping("/{exerciseId}")
    fun delete(
        @PathVariable trainingId: UUID,
        @PathVariable exerciseId: UUID,
        @CurrentUser ownerId: UUID,
    ): ResponseEntity<Void> {
        trainingService.deleteExercise(trainingId, exerciseId, ownerId)
        return ResponseEntity.noContent().build()
    }
}
