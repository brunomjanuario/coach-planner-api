package com.coachplanner.api.reference

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.reference.dto.ReferenceEntryDto
import com.coachplanner.api.reference.dto.ReferenceEntryRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/competitions")
class CompetitionController(private val service: ReferenceListService<Competition>) {

    @GetMapping
    fun getAll(@CurrentUser ownerId: UUID): List<ReferenceEntryDto> = service.getAll(ownerId)

    @PostMapping
    fun create(@CurrentUser ownerId: UUID, @Valid @RequestBody request: ReferenceEntryRequest): ResponseEntity<ReferenceEntryDto> {
        val created = service.create(ownerId, request.name)
        return ResponseEntity.created(URI.create("/api/v1/competitions/${created.id}")).body(created)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @CurrentUser ownerId: UUID): ResponseEntity<Void> {
        service.delete(id, ownerId)
        return ResponseEntity.noContent().build()
    }
}
