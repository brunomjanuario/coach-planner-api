package com.coachplanner.api.discipline

import com.coachplanner.api.common.newId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/** Lowercase to match Postgres's card_type labels exactly ('yellow','red') — NAMED_ENUM binds by Enum.name(). */
enum class CardType { yellow, red }

@Entity
@Table(name = "cards")
class Card(
    @Id
    val id: UUID = newId(),

    /** Raw scalar, not @ManyToOne — cards are reached only by playerId/gameId query filters (spec.md CARD-01), no bidirectional collection needed. */
    @Column(name = "player_id", nullable = false)
    var playerId: UUID,

    @Column(name = "game_id", nullable = false)
    var gameId: UUID,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    var type: CardType,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,
)
