package com.coachplanner.api.team.dto

import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerPosition
import java.util.UUID

data class PlayerDto(
    val id: UUID,
    val teamId: UUID,
    val name: String,
    val age: Int?,
    val shirtNumber: Int?,
    val position: PlayerPosition?,
    val goals: Int,
    val assists: Int,
    val concededGoals: Int,
) {
    companion object {
        fun from(player: Player) = PlayerDto(
            id = player.id,
            teamId = player.team.id,
            name = player.name,
            age = player.age,
            shirtNumber = player.shirtNumber,
            position = player.position,
            goals = player.goals,
            assists = player.assists,
            concededGoals = player.concededGoals,
        )
    }
}
