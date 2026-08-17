package com.coachplanner.api.game

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.game.dto.CreateGameRequest
import com.coachplanner.api.game.dto.GameDto
import com.coachplanner.api.game.dto.UpdateGameRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GameService(private val gameRepository: GameRepository) {

    @Transactional(readOnly = true)
    fun getById(id: UUID, ownerId: UUID): GameDto = GameDto.from(findOwned(id, ownerId))

    /** Scores are always created null — CreateGameRequest has no usScore/themScore field to bind from (AC GAME-03). */
    @Transactional
    fun create(ownerId: UUID, request: CreateGameRequest): GameDto {
        val game = Game(
            ownerId = ownerId,
            teamId = request.teamId,
            opponent = request.opponent.trim(),
            competition = request.competition?.trim(),
            date = request.date,
            isHome = request.isHome,
        )
        return GameDto.from(gameRepository.saveAndFlush(game))
    }

    /** Scores move only through the result endpoints — a body carrying either is 400, before anything else is applied (AC GAME-04). */
    @Transactional
    fun update(id: UUID, ownerId: UUID, request: UpdateGameRequest): GameDto {
        if (request.usScore != null || request.themScore != null) {
            throw ValidationException(
                "Scores can only be changed via PUT /games/{id}/result.",
                "scores-not-patchable",
            )
        }

        val game = findOwned(id, ownerId)
        request.teamId?.let { game.teamId = it }
        request.opponent?.let {
            val trimmed = it.trim()
            if (trimmed.isEmpty()) throw ValidationException("must not be blank")
            game.opponent = trimmed
        }
        request.competition?.let { game.competition = it.trim() }
        request.date?.let { game.date = it }
        request.isHome?.let { game.isHome = it }

        return GameDto.from(gameRepository.saveAndFlush(game))
    }

    private fun findOwned(id: UUID, ownerId: UUID): Game =
        gameRepository.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Game not found.")
}
