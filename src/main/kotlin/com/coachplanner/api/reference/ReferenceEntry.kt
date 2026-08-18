package com.coachplanner.api.reference

import java.util.UUID

/**
 * The shared shape of both managed reference lists (AD-104) — what lets
 * `ReferenceListService` be genuinely generic rather than parameterised by
 * copy-pasted boilerplate. `Competition` and `Opponent` differ only in which
 * `games` column their rename cascades to; everything else about them is
 * identical.
 */
interface ReferenceEntry {
    val id: UUID
    var ownerId: UUID
    var name: String
}
