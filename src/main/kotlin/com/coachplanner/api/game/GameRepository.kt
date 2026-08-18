package com.coachplanner.api.game

import com.coachplanner.api.common.OwnedRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GameRepository : OwnedRepository<Game, UUID> {

    /**
     * The rename cascade's own lookup (AD-104): matches case-insensitively
     * on the trimmed stored value, mirroring the frontend's `normalize()`.
     * A `null` `competition`/`opponent` never matches — SQL `NULL` compares
     * false against anything, including another `NULL`.
     */
    @Query("select g from Game g where g.ownerId = :ownerId and lower(trim(g.competition)) = lower(trim(:name))")
    fun findAllByOwnerIdAndCompetitionIgnoreCase(@Param("ownerId") ownerId: UUID, @Param("name") name: String): List<Game>

    @Query("select g from Game g where g.ownerId = :ownerId and lower(trim(g.opponent)) = lower(trim(:name))")
    fun findAllByOwnerIdAndOpponentIgnoreCase(@Param("ownerId") ownerId: UUID, @Param("name") name: String): List<Game>
}
