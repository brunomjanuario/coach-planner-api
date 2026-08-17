package com.coachplanner.api.discipline

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * `Rating` carries no `owner_id` of its own (design.md) — every row belongs
 * to exactly one of a training or a game (AD-106's `exactly_one_event`
 * CHECK), and both of those are owner-scoped, so ownership here is derived
 * through whichever FK is set rather than duplicated onto `ratings`.
 */
interface RatingRepository : JpaRepository<Rating, UUID> {

    @Query(
        """
        select r from Rating r
        where (r.trainingId is not null and r.trainingId in (select t.id from Training t where t.ownerId = :ownerId))
           or (r.gameId is not null and r.gameId in (select g.id from Game g where g.ownerId = :ownerId))
        """,
    )
    fun findAllByOwnerId(@Param("ownerId") ownerId: UUID): List<Rating>

    @Query(
        """
        select r from Rating r
        where r.id = :id
          and ((r.trainingId is not null and r.trainingId in (select t.id from Training t where t.ownerId = :ownerId))
            or (r.gameId is not null and r.gameId in (select g.id from Game g where g.ownerId = :ownerId)))
        """,
    )
    fun findByIdAndOwnerId(@Param("id") id: UUID, @Param("ownerId") ownerId: UUID): Rating?

    /** The upsert's own lookup keys (T40) — one partial unique index backs each. */
    fun findByPlayerIdAndTrainingId(playerId: UUID, trainingId: UUID): Rating?

    fun findByPlayerIdAndGameId(playerId: UUID, gameId: UUID): Rating?
}
