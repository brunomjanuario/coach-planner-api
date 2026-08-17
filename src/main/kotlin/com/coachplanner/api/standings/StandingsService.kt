package com.coachplanner.api.standings

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.standings.dto.StandingsRowDto
import com.coachplanner.api.team.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StandingsService(
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
    private val rivalRowRepository: RivalRowRepository,
) {

    /** The caller's own row (computed from that team's games) plus every rival row, sorted as one table (AC STAND-11). */
    @Transactional(readOnly = true)
    fun getStandings(ownerId: UUID, teamId: UUID): List<StandingsRowDto> {
        val team = teamRepository.findByIdAndOwnerId(teamId, ownerId) ?: throw NotFoundException("Team not found.")
        val teamGames = gameRepository.findAllByOwnerId(ownerId).filter { it.teamId == teamId }

        val ourRow = StandingsCalculator.computeOurRow(team.name, teamGames)
        val rivalRows = rivalRowRepository.findAllByOwnerId(ownerId).map { StandingsCalculator.fromRivalRow(it) }

        return StandingsCalculator.sort(listOf(ourRow) + rivalRows)
    }
}
