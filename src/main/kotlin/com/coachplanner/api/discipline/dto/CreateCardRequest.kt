package com.coachplanner.api.discipline.dto

import com.coachplanner.api.discipline.CardType
import java.util.UUID

/**
 * `type` is the real `CardType` enum, not a `String` — an unrecognised value
 * like `"orange"` fails Jackson's enum coercion in the message-converter
 * layer before the controller runs, hitting `ApiExceptionHandler`'s
 * `HttpMessageNotReadableException` handler for a `400` (same mechanism as
 * `PlayerPosition`, T24).
 */
data class CreateCardRequest(
    val playerId: UUID,
    val gameId: UUID,
    val type: CardType,
)
