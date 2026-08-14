package com.coachplanner.api.team

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.team.dto.CreateTeamRequest
import com.coachplanner.api.team.dto.TeamDto
import com.coachplanner.api.team.dto.UpdateTeamRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TeamService(private val teamRepository: TeamRepository) {

    /** readOnly transaction, not just a plain read: Team.players is lazy, and mapping to TeamDto needs the session still open. */
    @Transactional(readOnly = true)
    fun getAll(ownerId: UUID): List<TeamDto> = teamRepository.findAllByOwnerId(ownerId).map { TeamDto.from(it) }

    @Transactional(readOnly = true)
    fun getById(id: UUID, ownerId: UUID): TeamDto = TeamDto.from(findOwned(id, ownerId))

    @Transactional
    fun create(ownerId: UUID, request: CreateTeamRequest): TeamDto {
        val team = Team(ownerId = ownerId, name = request.name.trim(), club = request.club?.trim(), season = request.season?.trim())
        return TeamDto.from(teamRepository.saveAndFlush(team))
    }

    /** Only the supplied fields change — players is never touched here (AC TEAM-04). */
    @Transactional
    fun update(id: UUID, ownerId: UUID, request: UpdateTeamRequest): TeamDto {
        val team = findOwned(id, ownerId)

        request.name?.let {
            val trimmed = it.trim()
            if (trimmed.isEmpty()) throw ValidationException("must not be blank")
            team.name = trimmed
        }
        request.club?.let { team.club = it.trim() }
        request.season?.let { team.season = it.trim() }

        return TeamDto.from(teamRepository.saveAndFlush(team))
    }

    /**
     * No manual cascade code: deleting the Team relies on the schema's FK
     * actions (AD-107) — trainings/games survive with team_id nulled
     * (they're not part of Team's JPA object graph at all, so only the DB
     * FK action touches them), and cards/ratings are removed transitively
     * once their owning player row is gone.
     */
    @Transactional
    fun delete(id: UUID, ownerId: UUID) {
        teamRepository.delete(findOwned(id, ownerId))
    }

    private fun findOwned(id: UUID, ownerId: UUID): Team =
        teamRepository.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Team not found.")
}
