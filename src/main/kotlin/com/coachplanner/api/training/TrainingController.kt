package com.coachplanner.api.training

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.training.dto.CreateTrainingRequest
import com.coachplanner.api.training.dto.TrainingDto
import com.coachplanner.api.training.dto.UpdateTrainingRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/trainings")
class TrainingController(private val trainingService: TrainingService) {

    @GetMapping
    fun getAll(
        @CurrentUser ownerId: UUID,
        @RequestParam(required = false) teamId: UUID?,
        @RequestParam(required = false) assigned: Boolean?,
    ): List<TrainingDto> = trainingService.getAll(ownerId, teamId, assigned)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID, @CurrentUser ownerId: UUID): TrainingDto = trainingService.getById(id, ownerId)

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

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @CurrentUser ownerId: UUID): ResponseEntity<Void> {
        trainingService.delete(id, ownerId)
        return ResponseEntity.noContent().build()
    }
}
