package com.coachplanner.api.standings

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.standings.dto.CreateRivalRowRequest
import com.coachplanner.api.standings.dto.RivalRowDto
import com.coachplanner.api.standings.dto.StandingsRowDto
import com.coachplanner.api.standings.dto.UpdateRivalRowRequest
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
@RequestMapping("/api/v1/standings")
class StandingsController(
    private val rivalRowService: RivalRowService,
    private val standingsService: StandingsService,
) {

    @GetMapping
    fun getStandings(@CurrentUser ownerId: UUID, @RequestParam teamId: UUID?): List<StandingsRowDto> {
        if (teamId == null) throw ValidationException("teamId is required.", "missing-parameter")
        return standingsService.getStandings(ownerId, teamId)
    }

    @GetMapping("/rivals")
    fun getAllRivals(@CurrentUser ownerId: UUID): List<RivalRowDto> = rivalRowService.getAll(ownerId)

    @PostMapping("/rivals")
    fun createRival(@CurrentUser ownerId: UUID, @Valid @RequestBody request: CreateRivalRowRequest): ResponseEntity<RivalRowDto> {
        val created = rivalRowService.create(ownerId, request)
        return ResponseEntity.created(URI.create("/api/v1/standings/rivals/${created.id}")).body(created)
    }

    @PatchMapping("/rivals/{id}")
    fun updateRival(
        @PathVariable id: UUID,
        @CurrentUser ownerId: UUID,
        @Valid @RequestBody request: UpdateRivalRowRequest,
    ): RivalRowDto = rivalRowService.update(id, ownerId, request)

    @DeleteMapping("/rivals/{id}")
    fun deleteRival(@PathVariable id: UUID, @CurrentUser ownerId: UUID): ResponseEntity<Void> {
        rivalRowService.delete(id, ownerId)
        return ResponseEntity.noContent().build()
    }
}
