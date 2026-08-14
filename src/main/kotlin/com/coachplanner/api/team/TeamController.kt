package com.coachplanner.api.team

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.team.dto.CreateTeamRequest
import com.coachplanner.api.team.dto.TeamDto
import com.coachplanner.api.team.dto.UpdateTeamRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/teams")
class TeamController(private val teamService: TeamService) {

    @GetMapping
    fun getAll(@CurrentUser ownerId: UUID): List<TeamDto> = teamService.getAll(ownerId)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID, @CurrentUser ownerId: UUID): TeamDto = teamService.getById(id, ownerId)

    @PostMapping
    fun create(@CurrentUser ownerId: UUID, @Valid @RequestBody request: CreateTeamRequest): ResponseEntity<TeamDto> {
        val created = teamService.create(ownerId, request)
        return ResponseEntity.created(URI.create("/api/v1/teams/${created.id}")).body(created)
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @CurrentUser ownerId: UUID,
        @RequestBody request: UpdateTeamRequest,
    ): TeamDto = teamService.update(id, ownerId, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @CurrentUser ownerId: UUID): ResponseEntity<Void> {
        teamService.delete(id, ownerId)
        return ResponseEntity.noContent().build()
    }
}
