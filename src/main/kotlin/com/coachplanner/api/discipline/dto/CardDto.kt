package com.coachplanner.api.discipline.dto

import com.coachplanner.api.discipline.Card
import com.coachplanner.api.discipline.CardType
import java.util.UUID

data class CardDto(
    val id: UUID,
    val playerId: UUID,
    val gameId: UUID,
    val type: CardType,
) {
    companion object {
        fun from(card: Card) = CardDto(id = card.id, playerId = card.playerId, gameId = card.gameId, type = card.type)
    }
}
