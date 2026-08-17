package com.coachplanner.api.training

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.training.dto.CreateTrainingRequest
import com.coachplanner.api.training.dto.TrainingDto
import com.coachplanner.api.training.dto.UpdateTrainingRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/trainings")
class TrainingController(private val trainingService: TrainingService) {

    @PostMapping
    fun create(@CurrentUser ownerId: UUID, @Valid @RequestBody request: CreateTrainingRequest): ResponseEntity<TrainingDto> {
        val created = trainingService.create(ownerId, request)
        return ResponseEntity.created(URI.create("/api/v1/trainings/${created.id}")).body(created)
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @CurrentUser ownerId: UUID,
        @Valid @RequestBody request: UpdateTrainingRequest,
    ): TrainingDto = trainingService.update(id, ownerId, request)
}
