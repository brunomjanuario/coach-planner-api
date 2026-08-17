package com.coachplanner.api.discipline.dto

import java.util.UUID

/** `eventType`/`eventId` are derived from the storage FK columns (AD-106), not stored fields — see `RatingMapper`. */
data class RatingDto(
    val id: UUID,
    val playerId: UUID,
    val eventType: String,
    val eventId: UUID,
    val value: Int,
)
