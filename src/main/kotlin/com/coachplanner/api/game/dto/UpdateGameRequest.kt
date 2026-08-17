package com.coachplanner.api.game.dto

import java.time.Instant
import java.util.UUID

/**
 * `usScore`/`themScore` are declared here — unlike `CreateGameRequest` —
 * purely so the service can detect their presence and reject them with
 * `400` (AC GAME-04): scores move only through `PUT`/`DELETE
 * /games/{id}/result`, never through this endpoint.
 */
data class UpdateGameRequest(
    val teamId: UUID? = null,
    val opponent: String? = null,
    val competition: String? = null,
    val date: Instant? = null,
    val isHome: Boolean? = null,
    val usScore: Int? = null,
    val themScore: Int? = null,
)
