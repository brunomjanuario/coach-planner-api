package com.coachplanner.api.discipline

import com.coachplanner.api.common.NotFoundException
import com.coachplanner.api.common.ValidationException
import com.coachplanner.api.discipline.dto.RatingDto
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.training.TrainingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class RatingService(
    private val ratingRepository: RatingRepository,
    private val trainingRepository: TrainingRepository,
    private val gameRepository: GameRepository,
    transactionManager: PlatformTransactionManager,
) {

    /**
     * The insert attempt and its retry-as-update must each commit or roll
     * back independently: Postgres aborts a transaction entirely on any SQL
     * error, so a failed insert cannot be followed by a SELECT/UPDATE in the
     * *same* transaction. REQUIRES_NEW gives each attempt its own —
     * same pattern as RefreshTokenService's reuse-detection retry, and for
     * the same reason (self-invocation can't be intercepted by
     * @Transactional's proxy, so a TransactionTemplate is used explicitly).
     */
    private val newTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Transactional(readOnly = true)
    fun getAll(ownerId: UUID, eventType: String?, eventId: UUID?, playerId: UUID?): List<RatingDto> =
        ratingRepository.findAllByOwnerId(ownerId)
            .filter { eventType == null || matchesEventType(it, eventType) }
            .filter { eventId == null || matchesEventId(it, eventId) }
            .filter { playerId == null || it.playerId == playerId }
            .map { RatingMapper.toDto(it) }

    /**
     * Insert-then-retry-as-update, not check-then-act (AC RATE-07): two
     * concurrent calls for the same (player, event) triple can both observe
     * "no existing row" and both attempt an insert. The database's own
     * partial unique index (`uq_ratings_player_training`/
     * `uq_ratings_player_game`) lets exactly one succeed; the loser's insert
     * fails with a constraint violation, and it retries as an update in a
     * fresh transaction — concurrency is resolved by the index, not by
     * application-level locking.
     *
     * `value: null` deletes any existing row for the triple and returns
     * `null` (AC RATE-08) — a rating is never stored with a null value, and
     * `0` is stored as a real value, distinct from "no row" (AC RATE-09).
     *
     * Deliberately not `@Transactional` itself: every branch already manages
     * its own transaction (Spring Data's individually-transactional
     * repository calls, or the explicit REQUIRES_NEW template above). Wrapping
     * the whole method in an outer transaction would hold a connection for
     * its entire duration *in addition to* the REQUIRES_NEW block's own
     * connection on every call — not just the rare retry path — starving the
     * pool under concurrent load exactly like the race this method exists to
     * fix.
     */
    fun upsert(ownerId: UUID, eventType: String, eventId: UUID, playerId: UUID, value: Int?): RatingDto? {
        val event = resolveEvent(ownerId, eventType, eventId)

        if (value == null) {
            event.findExisting(ratingRepository, playerId)?.let { ratingRepository.delete(it) }
            return null
        }

        return try {
            newTransaction.execute<RatingDto> {
                val rating = event.newRating(playerId, value)
                RatingMapper.toDto(ratingRepository.saveAndFlush(rating))
            }
        } catch (ex: DataIntegrityViolationException) {
            newTransaction.execute<RatingDto> {
                val existing = event.findExisting(ratingRepository, playerId) ?: throw ex
                existing.value = value.toShort()
                RatingMapper.toDto(ratingRepository.saveAndFlush(existing))
            }
        }
    }

    @Transactional
    fun delete(id: UUID, ownerId: UUID) {
        val rating = ratingRepository.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Rating not found.")
        ratingRepository.delete(rating)
    }

    private fun matchesEventType(rating: Rating, eventType: String): Boolean =
        when (eventType) {
            "training" -> rating.trainingId != null
            "game" -> rating.gameId != null
            else -> false
        }

    private fun matchesEventId(rating: Rating, eventId: UUID): Boolean =
        rating.trainingId == eventId || rating.gameId == eventId

    /**
     * `eventType` neither "training" nor "game", or the named event doesn't
     * exist or isn't the caller's, is 400 (AC RATE-11) — like Training's
     * body `teamId` (T28), the failing reference is a path segment, not the
     * endpoint's own primary resource, so the usual not-found convention
     * doesn't apply here.
     */
    private fun resolveEvent(ownerId: UUID, eventType: String, eventId: UUID): ResolvedEvent =
        when (eventType) {
            "training" -> {
                trainingRepository.findByIdAndOwnerId(eventId, ownerId)
                    ?: throw ValidationException("Unknown event: $eventType/$eventId", "unknown-event")
                ResolvedEvent.TrainingEvent(eventId)
            }
            "game" -> {
                gameRepository.findByIdAndOwnerId(eventId, ownerId)
                    ?: throw ValidationException("Unknown event: $eventType/$eventId", "unknown-event")
                ResolvedEvent.GameEvent(eventId)
            }
            else -> throw ValidationException("eventType must be 'training' or 'game'.", "unknown-event")
        }

    private sealed class ResolvedEvent {
        abstract fun newRating(playerId: UUID, value: Int): Rating
        abstract fun findExisting(ratingRepository: RatingRepository, playerId: UUID): Rating?

        data class TrainingEvent(val id: UUID) : ResolvedEvent() {
            override fun newRating(playerId: UUID, value: Int) = Rating(playerId = playerId, trainingId = id, value = value.toShort())
            override fun findExisting(ratingRepository: RatingRepository, playerId: UUID) =
                ratingRepository.findByPlayerIdAndTrainingId(playerId, id)
        }

        data class GameEvent(val id: UUID) : ResolvedEvent() {
            override fun newRating(playerId: UUID, value: Int) = Rating(playerId = playerId, gameId = id, value = value.toShort())
            override fun findExisting(ratingRepository: RatingRepository, playerId: UUID) =
                ratingRepository.findByPlayerIdAndGameId(playerId, id)
        }
    }
}
