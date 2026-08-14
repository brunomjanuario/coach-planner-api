package com.coachplanner.api.team

import com.coachplanner.api.common.newId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/** Matches Postgres's `player_position` enum labels exactly (V1__init.sql) — mirrors the frontend's Positions map. */
enum class PlayerPosition {
    GK, RB, CB, LB, RWB, LWB, CDM, RM, CM, LM, CAM, RW, LW, ST, CF, RF, LF
}

@Entity
@Table(name = "players")
class Player(
    @Id
    val id: UUID = newId(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    var team: Team,

    @Column(nullable = false)
    var name: String,

    var age: Int? = null,

    @Column(name = "shirt_number")
    var shirtNumber: Int? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    var position: PlayerPosition? = null,

    @Column(nullable = false)
    var goals: Int = 0,

    @Column(nullable = false)
    var assists: Int = 0,

    @Column(name = "conceded_goals", nullable = false)
    var concededGoals: Int = 0,

    @Version
    var version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
