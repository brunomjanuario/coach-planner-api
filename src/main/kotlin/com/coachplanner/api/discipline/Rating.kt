package com.coachplanner.api.discipline

import com.coachplanner.api.common.newId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

/**
 * Two nullable FKs, not a polymorphic (eventType, eventId) pair (AD-106) —
 * the DB's exactly_one_event CHECK enforces exactly one is set. The API
 * layer derives eventType/eventId from whichever is non-null.
 */
@Entity
@Table(name = "ratings")
class Rating(
    @Id
    val id: UUID = newId(),

    @Column(name = "player_id", nullable = false)
    var playerId: UUID,

    @Column(name = "training_id")
    var trainingId: UUID? = null,

    @Column(name = "game_id")
    var gameId: UUID? = null,

    /** smallint's exact JDBC/Hibernate type is Short — an Int here would fail ddl-auto=validate. */
    @Column(nullable = false)
    var value: Short,

    @Version
    var version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
