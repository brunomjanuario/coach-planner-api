package com.coachplanner.api.discipline

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.discipline.dto.CardDto
import com.coachplanner.api.discipline.dto.CreateCardRequest
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.team.PlayerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CardService(
    private val cardRepository: CardRepository,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
) {

    @Transactional(readOnly = true)
    fun getAll(ownerId: UUID, gameId: UUID?, playerId: UUID?): List<CardDto> =
        cardRepository.findAllByOwnerId(ownerId)
            .filter { gameId == null || it.gameId == gameId }
            .filter { playerId == null || it.playerId == playerId }
            .map { CardDto.from(it) }

    /**
     * `gameId` must be the caller's own game (404 if not — `gameId` is the
     * card's own primary relation, unlike Training's body `teamId`, so the
     * usual not-found convention applies rather than a 400 reference error).
     * The player must be a member of that game's own team (AC CARD-03) —
     * this also naturally rejects an unassigned game (`teamId: null`), since
     * no player can belong to "no team".
     */
    @Transactional
    fun create(ownerId: UUID, request: CreateCardRequest): CardDto {
        val game = gameRepository.findByIdAndOwnerId(request.gameId, ownerId)
            ?: throw NotFoundException("Game not found.")

        val player = playerRepository.findById(request.playerId).orElse(null)
        if (player == null || player.team.id != game.teamId) {
            throw ValidationException(
                "Player ${request.playerId} is not in the team playing game ${request.gameId}.",
                "player-not-in-game-team",
            )
        }

        val card = Card(playerId = request.playerId, gameId = request.gameId, type = request.type)
        return CardDto.from(cardRepository.saveAndFlush(card))
    }

    @Transactional
    fun delete(id: UUID, ownerId: UUID) {
        val card = cardRepository.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Card not found.")
        cardRepository.delete(card)
    }
}
