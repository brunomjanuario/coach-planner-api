package com.coachplanner.api.training

import java.util.UUID

/**
 * Port of the frontend's `numberTrainings` (AD-108, frontend AD-006): a
 * training's display number is derived on read, never stored — so it cannot
 * drift from the data it describes.
 */
object TrainingNumbering {

    /**
     * Maps training id → its 1-based position **within its team**, ordered by
     * `day` ascending with ties broken by id string comparison (the frontend's
     * `String(a.id).localeCompare(String(b.id))`, which agrees with plain
     * string ordering over lowercase hex UUIDs — note this is deliberately
     * *not* `UUID.compareTo`, whose signed-long comparison orders differently).
     *
     * A training with no team is absent from the map, so a lookup yields null
     * (AC TRAIN-02).
     *
     * Callers must pass the owner's **whole** training history and filter
     * afterwards: numbering an already-filtered subset renumbers it from 1,
     * which is exactly the regression AC TRAIN-03 forbids.
     */
    fun numbersById(trainings: List<Training>): Map<UUID, Int> =
        trainings
            .filter { it.teamId != null }
            .groupBy { it.teamId }
            .values
            .flatMap { group ->
                group
                    .sortedWith(compareBy<Training> { it.day }.thenBy { it.id.toString() })
                    .mapIndexed { index, training -> training.id to index + 1 }
            }
            .toMap()
}
