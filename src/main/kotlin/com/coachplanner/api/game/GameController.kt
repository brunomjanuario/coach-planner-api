package com.coachplanner.api.game

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.game.dto.CreateGameRequest
import com.coachplanner.api.game.dto.GameDto
import com.coachplanner.api.game.dto.UpdateGameRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
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
@RequestMapping("/api/v1/games")
class GameController(private val gameService: GameService) {

    @GetMapping
    fun getAll(
        @CurrentUser ownerId: UUID,
        @RequestParam(required = false) teamId: UUID?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) assigned: Boolean?,
    ): List<GameDto> = gameService.getAll(ownerId, teamId, status, assigned)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID, @CurrentUser ownerId: UUID): GameDto = gameService.getById(id, ownerId)

    @PostMapping
    fun create(@CurrentUser ownerId: UUID, @Valid @RequestBody request: CreateGameRequest): ResponseEntity<GameDto> {
        val created = gameService.create(ownerId, request)
        return ResponseEntity.created(URI.create("/api/v1/games/${created.id}")).body(created)
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @CurrentUser ownerId: UUID,
        @RequestBody request: UpdateGameRequest,
    ): GameDto = gameService.update(id, ownerId, request)
}
