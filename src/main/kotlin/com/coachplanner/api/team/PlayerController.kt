package com.coachplanner.api.team

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.team.dto.CreatePlayerRequest
import com.coachplanner.api.team.dto.PlayerDto
import com.coachplanner.api.team.dto.UpdatePlayerRequest
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
@RequestMapping("/api/v1/teams/{teamId}/players")
class PlayerController(private val playerService: PlayerService) {

    @GetMapping
    fun getAll(@PathVariable teamId: UUID, @CurrentUser ownerId: UUID): List<PlayerDto> =
        playerService.getAll(teamId, ownerId)

    @GetMapping("/{playerId}")
    fun getById(
        @PathVariable teamId: UUID,
        @PathVariable playerId: UUID,
        @CurrentUser ownerId: UUID,
    ): PlayerDto = playerService.getById(teamId, playerId, ownerId)

    @PostMapping
    fun create(
        @PathVariable teamId: UUID,
        @CurrentUser ownerId: UUID,
        @Valid @RequestBody request: CreatePlayerRequest,
    ): ResponseEntity<PlayerDto> {
        val created = playerService.create(teamId, ownerId, request)
        return ResponseEntity.created(URI.create("/api/v1/teams/$teamId/players/${created.id}")).body(created)
    }

    @PatchMapping("/{playerId}")
    fun update(
        @PathVariable teamId: UUID,
        @PathVariable playerId: UUID,
        @CurrentUser ownerId: UUID,
        @Valid @RequestBody request: UpdatePlayerRequest,
    ): PlayerDto = playerService.update(teamId, playerId, ownerId, request)

    @DeleteMapping("/{playerId}")
    fun delete(
        @PathVariable teamId: UUID,
        @PathVariable playerId: UUID,
        @CurrentUser ownerId: UUID,
    ): ResponseEntity<Void> {
        playerService.delete(teamId, playerId, ownerId)
        return ResponseEntity.noContent().build()
    }
}
