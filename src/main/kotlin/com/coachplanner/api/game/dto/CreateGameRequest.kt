package com.coachplanner.api.game.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/**
 * Deliberately carries no `usScore`/`themScore` field: the API ignores
 * unknown JSON properties (JacksonConfig), so a caller supplying scores on
 * create has them silently dropped — exactly AC GAME-03's requirement,
 * with no explicit rejection code needed.
 */
data class CreateGameRequest(
    val teamId: UUID? = null,

    @field:NotBlank(message = "must not be blank")
    val opponent: String,

    val competition: String? = null,

    val date: Instant,

    val isHome: Boolean,
)
