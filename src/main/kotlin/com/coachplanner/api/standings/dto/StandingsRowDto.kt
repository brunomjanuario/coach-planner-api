package com.coachplanner.api.standings.dto

/** The combined-table shape (AD-108): `points`/`goalDifference` are always derived here, on both "our" and rival rows. */
data class StandingsRowDto(
    val name: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int,
    val isOurs: Boolean,
)
