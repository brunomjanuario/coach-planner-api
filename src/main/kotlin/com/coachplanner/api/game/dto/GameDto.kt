package com.coachplanner.api.game.dto

import com.coachplanner.api.game.Game
import java.time.Instant
import java.util.UUID

/** `outcome` is never sent (AD-108) — the frontend derives it from `usScore`/`themScore` itself. */
data class GameDto(
    val id: UUID,
    val teamId: UUID?,
    val opponent: String,
    val competition: String?,
    val date: Instant,
    val isHome: Boolean,
    val usScore: Int?,
    val themScore: Int?,
) {
    companion object {
        fun from(game: Game) = GameDto(
            id = game.id,
            teamId = game.teamId,
            opponent = game.opponent,
            competition = game.competition,
            date = game.date,
            isHome = game.isHome,
            usScore = game.usScore,
            themScore = game.themScore,
        )
    }
}
