package com.coachplanner.api.team

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.team.dto.CreatePlayerRequest
import com.coachplanner.api.team.dto.PlayerDto
import com.coachplanner.api.team.dto.UpdatePlayerRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PlayerService(
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
) {

    @Transactional
    fun create(teamId: UUID, ownerId: UUID, request: CreatePlayerRequest): PlayerDto {
        val team = findOwnedTeam(teamId, ownerId)
        val player = Player(
            team = team,
            name = request.name.trim(),
            age = request.age,
            shirtNumber = request.shirtNumber,
            position = request.position,
        )
        return PlayerDto.from(playerRepository.saveAndFlush(player))
    }

    /** A stats-only PATCH (goals/assists/concededGoals) is the frontend's own popup can't do this — that's the point of exposing it here. */
    @Transactional
    fun update(teamId: UUID, playerId: UUID, ownerId: UUID, request: UpdatePlayerRequest): PlayerDto {
        val player = findOwnedPlayer(teamId, playerId, ownerId)

        request.name?.let { player.name = it.trim() }
        request.age?.let { player.age = it }
        request.shirtNumber?.let { player.shirtNumber = it }
        request.position?.let { player.position = it }
        request.goals?.let { player.goals = it }
        request.assists?.let { player.assists = it }
        request.concededGoals?.let { player.concededGoals = it }

        return PlayerDto.from(playerRepository.saveAndFlush(player))
    }

    /** A playerId that exists but under a different teamId in the path is a 404 (AC PLAY-11) — not found via this team, full stop. */
    fun findOwnedPlayer(teamId: UUID, playerId: UUID, ownerId: UUID): Player {
        findOwnedTeam(teamId, ownerId)
        val player = playerRepository.findById(playerId).orElseThrow { NotFoundException("Player not found.") }
        if (player.team.id != teamId) throw NotFoundException("Player not found.")
        return player
    }

    private fun findOwnedTeam(teamId: UUID, ownerId: UUID): Team =
        teamRepository.findByIdAndOwnerId(teamId, ownerId) ?: throw NotFoundException("Team not found.")
}
