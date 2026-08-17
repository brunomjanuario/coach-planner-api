package com.coachplanner.api.standings

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.standings.dto.CreateRivalRowRequest
import com.coachplanner.api.standings.dto.RivalRowDto
import com.coachplanner.api.standings.dto.UpdateRivalRowRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RivalRowService(private val rivalRowRepository: RivalRowRepository) {

    @Transactional(readOnly = true)
    fun getAll(ownerId: UUID): List<RivalRowDto> = rivalRowRepository.findAllByOwnerId(ownerId).map { RivalRowDto.from(it) }

    @Transactional
    fun create(ownerId: UUID, request: CreateRivalRowRequest): RivalRowDto {
        requireConsistent(request.played, request.won, request.drawn, request.lost)

        val row = RivalRow(
            ownerId = ownerId,
            name = request.name.trim(),
            played = request.played,
            won = request.won,
            drawn = request.drawn,
            lost = request.lost,
            goalsFor = request.goalsFor,
            goalsAgainst = request.goalsAgainst,
        )
        return RivalRowDto.from(rivalRowRepository.saveAndFlush(row))
    }

    @Transactional
    fun update(id: UUID, ownerId: UUID, request: UpdateRivalRowRequest): RivalRowDto {
        val row = findOwned(id, ownerId)

        request.name?.let {
            val trimmed = it.trim()
            if (trimmed.isEmpty()) throw ValidationException("must not be blank")
            row.name = trimmed
        }
        request.played?.let { row.played = it }
        request.won?.let { row.won = it }
        request.drawn?.let { row.drawn = it }
        request.lost?.let { row.lost = it }
        request.goalsFor?.let { row.goalsFor = it }
        request.goalsAgainst?.let { row.goalsAgainst = it }

        // Validated on the resulting row, not just the patched fields: a partial update must not leave the invariant broken either.
        requireConsistent(row.played, row.won, row.drawn, row.lost)

        return RivalRowDto.from(rivalRowRepository.saveAndFlush(row))
    }

    @Transactional
    fun delete(id: UUID, ownerId: UUID) {
        rivalRowRepository.delete(findOwned(id, ownerId))
    }

    /** AC STAND-09: the DB's own results_sum_to_played CHECK is the floor; this is the friendly, message-naming 400 above it. */
    private fun requireConsistent(played: Int, won: Int, drawn: Int, lost: Int) {
        val summed = won + drawn + lost
        if (summed != played) {
            throw ValidationException(
                "Won, drawn and lost ($summed) must add up to played ($played).",
                "rival-row-invalid",
            )
        }
    }

    private fun findOwned(id: UUID, ownerId: UUID): RivalRow =
        rivalRowRepository.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Rival row not found.")
}
