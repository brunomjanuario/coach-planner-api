package com.coachplanner.api.reference

import com.coachplanner.api.common.newId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** AD-104: a managed reference list of name strings, not a foreign key target. */
@Entity
@Table(name = "competitions")
class Competition(
    @Id
    val id: UUID = newId(),

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID,

    @Column(nullable = false)
    var name: String,
)
