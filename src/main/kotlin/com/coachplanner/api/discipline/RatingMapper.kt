package com.coachplanner.api.discipline

import com.coachplanner.api.discipline.dto.RatingDto

/**
 * Derives `eventType`/`eventId` from the two nullable FK columns (AD-106) —
 * the wire shape the frontend's polymorphic `(eventType, eventId)` pair
 * expects, distinct from the two-nullable-FK storage shape.
 */
object RatingMapper {
    fun toDto(rating: Rating): RatingDto {
        val (eventType, eventId) = if (rating.trainingId != null) {
            "training" to requireNotNull(rating.trainingId)
        } else {
            "game" to requireNotNull(rating.gameId)
        }
        return RatingDto(id = rating.id, playerId = rating.playerId, eventType = eventType, eventId = eventId, value = rating.value.toInt())
    }
}
