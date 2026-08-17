package com.coachplanner.api.discipline

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * `Card` carries no `owner_id` of its own (design.md) — every card belongs
 * to a game, and games are owner-scoped, so ownership is derived through
 * `game_id` rather than duplicated onto `cards`.
 */
interface CardRepository : JpaRepository<Card, UUID> {

    @Query("select c from Card c where c.gameId in (select g.id from Game g where g.ownerId = :ownerId)")
    fun findAllByOwnerId(@Param("ownerId") ownerId: UUID): List<Card>

    @Query("select c from Card c where c.id = :id and c.gameId in (select g.id from Game g where g.ownerId = :ownerId)")
    fun findByIdAndOwnerId(@Param("id") id: UUID, @Param("ownerId") ownerId: UUID): Card?
}
