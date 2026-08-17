package com.coachplanner.api.discipline

import com.coachplanner.api.discipline.dto.RatingDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RatingService(private val ratingRepository: RatingRepository) {

    @Transactional(readOnly = true)
    fun getAll(ownerId: UUID, eventType: String?, eventId: UUID?, playerId: UUID?): List<RatingDto> =
        ratingRepository.findAllByOwnerId(ownerId)
            .filter { eventType == null || matchesEventType(it, eventType) }
            .filter { eventId == null || matchesEventId(it, eventId) }
            .filter { playerId == null || it.playerId == playerId }
            .map { RatingMapper.toDto(it) }

    private fun matchesEventType(rating: Rating, eventType: String): Boolean =
        when (eventType) {
            "training" -> rating.trainingId != null
            "game" -> rating.gameId != null
            else -> false
        }

    private fun matchesEventId(rating: Rating, eventId: UUID): Boolean =
        rating.trainingId == eventId || rating.gameId == eventId
}
