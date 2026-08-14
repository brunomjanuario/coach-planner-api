package com.coachplanner.api.standings

import com.coachplanner.api.common.newId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

/** No points/goal_difference columns and no created_at/updated_at — see V1__init.sql; both figures are always derived (AD-108). */
@Entity
@Table(name = "standings_rivals")
class RivalRow(
    @Id
    val id: UUID = newId(),

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var played: Int,

    @Column(nullable = false)
    var won: Int,

    @Column(nullable = false)
    var drawn: Int,

    @Column(nullable = false)
    var lost: Int,

    @Column(name = "goals_for", nullable = false)
    var goalsFor: Int,

    @Column(name = "goals_against", nullable = false)
    var goalsAgainst: Int,

    @Version
    var version: Long = 0,
)
