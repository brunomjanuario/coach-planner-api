package com.coachplanner.api.team.dto

/** All fields optional — a PATCH updates only what's supplied and never touches `players` (AC TEAM-04). */
data class UpdateTeamRequest(
    val name: String? = null,
    val club: String? = null,
    val season: String? = null,
)
