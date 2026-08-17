package com.coachplanner.api.discipline

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.discipline.dto.CardDto
import com.coachplanner.api.discipline.dto.CreateCardRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/cards")
class CardController(private val cardService: CardService) {

    @GetMapping
    fun getAll(
        @CurrentUser ownerId: UUID,
        @RequestParam(required = false) gameId: UUID?,
        @RequestParam(required = false) playerId: UUID?,
    ): List<CardDto> = cardService.getAll(ownerId, gameId, playerId)

    @PostMapping
    fun create(@CurrentUser ownerId: UUID, @Valid @RequestBody request: CreateCardRequest): ResponseEntity<CardDto> {
        val created = cardService.create(ownerId, request)
        return ResponseEntity.created(URI.create("/api/v1/cards/${created.id}")).body(created)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @CurrentUser ownerId: UUID): ResponseEntity<Void> {
        cardService.delete(id, ownerId)
        return ResponseEntity.noContent().build()
    }
}
