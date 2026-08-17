package com.coachplanner.api.standings.dto

import com.coachplanner.api.standings.RivalRow
import java.util.UUID

/**
 * The raw, manually-maintained row — no `points`/`goalDifference` here,
 * mirroring `RivalRow` itself (both are always derived, never stored,
 * AD-108). The combined table's `StandingsRowDto` (T37) is where those
 * derived figures appear.
 */
data class RivalRowDto(
    val id: UUID,
    val name: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
) {
    companion object {
        fun from(row: RivalRow) = RivalRowDto(
            id = row.id,
            name = row.name,
            played = row.played,
            won = row.won,
            drawn = row.drawn,
            lost = row.lost,
            goalsFor = row.goalsFor,
            goalsAgainst = row.goalsAgainst,
        )
    }
}
