package com.coachplanner.api.team

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
@Table(name = "teams")
class Team(
    @Id
    val id: UUID = newId(),

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @Column(nullable = false)
    var name: String,

    var club: String? = null,

    var season: String? = null,

    /** cascade+orphanRemoval mirrors the DB's ON DELETE CASCADE — deleting a team deletes its players. */
    @OneToMany(mappedBy = "team", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("shirtNumber ASC, name ASC")
    var players: MutableList<Player> = mutableListOf(),

    @Version
    var version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
