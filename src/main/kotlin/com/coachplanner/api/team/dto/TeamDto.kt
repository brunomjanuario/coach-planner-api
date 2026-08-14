package com.coachplanner.api.team.dto

import com.coachplanner.api.team.Team
import java.util.UUID

data class TeamDto(
    val id: UUID,
    val name: String,
    val club: String?,
    val season: String?,
    val players: List<PlayerDto>,
) {
    companion object {
        fun from(team: Team) = TeamDto(
            id = team.id,
            name = team.name,
            club = team.club,
            season = team.season,
            players = team.players.map { PlayerDto.from(it) },
        )
    }
}
