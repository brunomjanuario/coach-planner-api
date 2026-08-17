package com.coachplanner.api.training

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.team.TeamRepository
import com.coachplanner.api.training.dto.CreateTrainingRequest
import com.coachplanner.api.training.dto.ExerciseRequest
import com.coachplanner.api.training.dto.TrainingDto
import com.coachplanner.api.training.dto.UpdateTrainingRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TrainingService(
    private val trainingRepository: TrainingRepository,
    private val teamRepository: TeamRepository,
    private val diagramValidator: DiagramValidator,
) {

    /**
     * Numbers are computed over the owner's *whole* history and only then
     * filtered (AC TRAIN-03) — computing after filtering would renumber a
     * team's trainings from 1 every time `?teamId=` narrows the result,
     * which is the exact regression tasks.md flags as this phase's
     * highest-risk mistake.
     */
    @Transactional(readOnly = true)
    fun getAll(ownerId: UUID, teamId: UUID?, assigned: Boolean?): List<TrainingDto> {
        val all = trainingRepository.findAllByOwnerId(ownerId)
        val numbers = TrainingNumbering.numbersById(all)

        return all
            .filter { teamId == null || it.teamId == teamId }
            .filter { assigned == null || (assigned == (it.teamId != null)) }
            .sortedWith(compareBy<Training> { it.day }.thenBy { it.id.toString() })
            .map { TrainingDto.from(it, numbers[it.id]) }
    }

    @Transactional(readOnly = true)
    fun getById(id: UUID, ownerId: UUID): TrainingDto {
        val training = findOwned(id, ownerId)
        val numbers = TrainingNumbering.numbersById(trainingRepository.findAllByOwnerId(ownerId))
        return TrainingDto.from(training, numbers[training.id])
    }

    @Transactional
    fun create(ownerId: UUID, request: CreateTrainingRequest): TrainingDto {
        val training = Training(
            ownerId = ownerId,
            teamId = requireOwnedTeam(request.teamId, ownerId),
            day = request.day,
            durationMinutes = request.duration,
        )
        replaceExercises(training, request.exercises)

        return toDto(trainingRepository.saveAndFlush(training), ownerId)
    }

    @Transactional
    fun update(id: UUID, ownerId: UUID, request: UpdateTrainingRequest): TrainingDto {
        val training = findOwned(id, ownerId)

        request.teamId?.let { training.teamId = requireOwnedTeam(it, ownerId) }
        request.day?.let { training.day = it }
        request.duration?.let { training.durationMinutes = it }
        // Present-but-empty is a real instruction (replace with nothing); absent is not (AC TRAIN-07/08).
        request.exercises?.let { replaceExercises(training, it) }

        return toDto(trainingRepository.saveAndFlush(training), ownerId)
    }

    /**
     * Wholesale replacement, not a merge: exercises have no client-visible
     * identity across an update (the frontend sends the whole array), so
     * clearing the collection and rebuilding it from array position is both
     * simpler and exactly what AC TRAIN-07 describes. orphanRemoval on
     * Training.exercises deletes the old rows; the unique
     * (training_id, order_index) index is DEFERRABLE so the inserts and
     * deletes may interleave within the transaction.
     */
    private fun replaceExercises(training: Training, requests: List<ExerciseRequest>) {
        training.exercises.clear()
        requests.forEachIndexed { index, request ->
            training.exercises.add(
                Exercise(
                    training = training,
                    orderIndex = index,
                    description = request.description.trim(),
                    numberOfPlayers = request.numberOfPlayers,
                    durationMinutes = request.duration,
                    repetitions = request.repetitions,
                    diagram = diagramValidator.validate(request.diagram),
                ),
            )
        }
    }

    /**
     * No manual cascade code: exercises (orphanRemoval on the JPA side) and
     * every rating for this training (the schema's `ON DELETE CASCADE` on
     * `ratings.training_id`, AD-106) go with it purely from the delete
     * itself — same pattern as Team delete (T23).
     */
    @Transactional
    fun delete(id: UUID, ownerId: UUID) {
        trainingRepository.delete(findOwned(id, ownerId))
    }

    /**
     * A `teamId` in the body naming a team the caller doesn't own is a 400
     * `unknown-team`, not a 404 (AC TRAIN-12): the endpoint's own subject —
     * the training — is fine; it's the body's reference that is unknown.
     * Same 404-not-403 rule underneath, since a team owned by someone else
     * is simply not found for this caller.
     */
    private fun requireOwnedTeam(teamId: UUID?, ownerId: UUID): UUID? {
        if (teamId == null) return null
        teamRepository.findByIdAndOwnerId(teamId, ownerId)
            ?: throw ValidationException("Unknown team: $teamId", "unknown-team")
        return teamId
    }

    /** The number is derived over the owner's whole history, never over a subset (AC TRAIN-03) — see TrainingNumbering. */
    private fun toDto(training: Training, ownerId: UUID): TrainingDto {
        val numbers = TrainingNumbering.numbersById(trainingRepository.findAllByOwnerId(ownerId))
        return TrainingDto.from(training, numbers[training.id])
    }

    private fun findOwned(id: UUID, ownerId: UUID): Training =
        trainingRepository.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Training not found.")
}
