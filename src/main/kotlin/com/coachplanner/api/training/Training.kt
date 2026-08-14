package com.coachplanner.api.training

import com.coachplanner.api.common.newId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "trainings")
class Training(
    @Id
    val id: UUID = newId(),

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    /**
     * Raw scalar, not @ManyToOne Team — nullable (a training can be
     * unassigned, ON DELETE SET NULL), and Team has no bidirectional
     * `trainings` collection to maintain. Query-filter FK, not a navigated
     * relationship (same reasoning as Team.ownerId in T7).
     */
    @Column(name = "team_id")
    var teamId: UUID? = null,

    @Column(nullable = false)
    var day: Instant,

    @Column(name = "duration_minutes", nullable = false)
    var durationMinutes: Int,

    /**
     * @OrderBy (a read-time SQL ORDER BY), not @OrderColumn: @OrderColumn's
     * automatic index management only works when the @OneToMany is the
     * *owning* side. This collection is the inverse side of Exercise's
     * @ManyToOne (mappedBy), and exercises.training_id is NOT NULL —
     * Hibernate cannot populate order_index from an inverse-side collection
     * on the child's own insert. Exercise.orderIndex is instead an explicit
     * field the caller sets (same pattern as Team.players' @OrderBy).
     */
    @OneToMany(mappedBy = "training", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    var exercises: MutableList<Exercise> = mutableListOf(),

    @Version
    var version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
