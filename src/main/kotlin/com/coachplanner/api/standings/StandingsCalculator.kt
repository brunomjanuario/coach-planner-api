package com.coachplanner.api.standings

import com.coachplanner.api.game.Game
import com.coachplanner.api.game.hasResult
import com.coachplanner.api.standings.dto.StandingsRowDto

/** Port of the frontend's `computeOurRow` + `toStandingsRow` + `sortStandings` (`lib/standings.js`, AD-108). */
object StandingsCalculator {

    /**
     * Only played games count (AC STAND-11) — a scheduled fixture
     * contributes nothing. Zero played games still yields a row, present
     * and all-zero, never absent (AC STAND-12).
     */
    fun computeOurRow(teamName: String, games: List<Game>): StandingsRowDto {
        val played = games.filter { hasResult(it) }

        var won = 0
        var drawn = 0
        var lost = 0
        var goalsFor = 0
        var goalsAgainst = 0

        for (game in played) {
            val us = requireNotNull(game.usScore)
            val them = requireNotNull(game.themScore)
            goalsFor += us
            goalsAgainst += them
            when {
                us > them -> won++
                us < them -> lost++
                else -> drawn++
            }
        }

        return toStandingsRow(teamName, played.size, won, drawn, lost, goalsFor, goalsAgainst, isOurs = true)
    }

    /** Normalizes a stored RivalRow to the same StandingsRowDto shape, so sort never has to special-case which rows are ours. */
    fun fromRivalRow(row: RivalRow): StandingsRowDto =
        toStandingsRow(row.name, row.played, row.won, row.drawn, row.lost, row.goalsFor, row.goalsAgainst, isOurs = false)

    /** Points desc, then goal difference desc, then goals-for desc, then name asc — a deterministic four-level tiebreak (AC STAND-11). */
    fun sort(rows: List<StandingsRowDto>): List<StandingsRowDto> =
        rows.sortedWith(
            compareByDescending<StandingsRowDto> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.name },
        )

    /** points/goalDifference are always derived here, never read from stored input (AD-108, frontend AD-008). */
    private fun toStandingsRow(
        name: String,
        played: Int,
        won: Int,
        drawn: Int,
        lost: Int,
        goalsFor: Int,
        goalsAgainst: Int,
        isOurs: Boolean,
    ) = StandingsRowDto(
        name = name,
        played = played,
        won = won,
        drawn = drawn,
        lost = lost,
        goalsFor = goalsFor,
        goalsAgainst = goalsAgainst,
        goalDifference = goalsFor - goalsAgainst,
        points = won * 3 + drawn,
        isOurs = isOurs,
    )
}
