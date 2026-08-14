package com.coachplanner.api.game

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

@Entity
@Table(name = "games")
class Game(
    @Id
    val id: UUID = newId(),

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    /** Raw scalar, not @ManyToOne Team — same reasoning as Training.teamId. */
    @Column(name = "team_id")
    var teamId: UUID? = null,

    @Column(nullable = false)
    var opponent: String,

    /** AD-104: a name string, not a foreign key to `opponents`/`competitions`. */
    var competition: String? = null,

    @Column(nullable = false)
    var date: Instant,

    @Column(name = "is_home", nullable = false)
    var isHome: Boolean,

    @Column(name = "us_score")
    var usScore: Int? = null,

    @Column(name = "them_score")
    var themScore: Int? = null,

    @Version
    var version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
