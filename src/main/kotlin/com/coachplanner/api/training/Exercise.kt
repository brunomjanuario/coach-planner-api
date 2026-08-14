package com.coachplanner.api.training

import com.coachplanner.api.common.newId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "exercises")
class Exercise(
    @Id
    val id: UUID = newId(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    var training: Training,

    /** Caller-set — see Training.exercises' @OrderBy for why this isn't a managed @OrderColumn. */
    @Column(name = "order_index", nullable = false)
    var orderIndex: Int,

    @Column(nullable = false)
    var description: String,

    @Column(name = "number_of_players")
    var numberOfPlayers: Int? = null,

    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,

    var repetitions: Int? = null,

    /**
     * A generic JSON object, not a typed diagram model — typed validation
     * (shape kinds, coordinate clamping, size/count limits) is P5/T30's job
     * per design.md; the entity stores whatever structure was validated
     * upstream. Map, not String: Hibernate's JSON JdbcType serializes the
     * Kotlin type given via a format mapper (Jackson) — a String field
     * would be double-encoded (wrapped as a JSON string) rather than
     * stored as the JSON structure itself.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    var diagram: Map<String, Any?>? = null,
)
