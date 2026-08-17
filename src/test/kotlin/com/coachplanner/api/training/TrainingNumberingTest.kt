package com.coachplanner.api.training

import com.coachplanner.api.common.newId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * tasks.md T27 / AC TRAIN-02 and the spec's "identical days" edge case.
 * Pure logic — no Spring context, no database.
 */
class TrainingNumberingTest {

    private val owner = newId()

    private fun training(team: UUID?, day: Instant, id: UUID = newId()) =
        Training(id = id, ownerId = owner, teamId = team, day = day, durationMinutes = 60)

    private fun day(dayOfMonth: Int): Instant = Instant.parse("2024-10-%02dT15:00:00Z".format(dayOfMonth))

    @Test
    fun `trainings supplied out of order are numbered by day ascending`() {
        val team = newId()
        val third = training(team, day(24))
        val first = training(team, day(10))
        val second = training(team, day(17))

        val numbers = TrainingNumbering.numbersById(listOf(third, first, second))

        assertEquals(1, numbers[first.id], "expected the earliest day to be number 1")
        assertEquals(2, numbers[second.id], "expected the middle day to be number 2")
        assertEquals(3, numbers[third.id], "expected the latest day to be number 3")
    }

    @Test
    fun `equal days break the tie by id, identically across repeated runs`() {
        val team = newId()
        val sameDay = day(10)
        val trainings = List(3) { training(team, sameDay) }
        val expected = trainings.sortedBy { it.id.toString() }

        repeat(20) { run ->
            val numbers = TrainingNumbering.numbersById(trainings.shuffled())

            assertEquals(1, numbers[expected[0].id], "run $run: expected the lowest id to be number 1")
            assertEquals(2, numbers[expected[1].id], "run $run: expected the middle id to be number 2")
            assertEquals(3, numbers[expected[2].id], "run $run: expected the highest id to be number 3")
        }
    }

    @Test
    fun `a training with no team gets no number`() {
        val unassigned = training(team = null, day = day(10))

        val numbers = TrainingNumbering.numbersById(listOf(unassigned))

        assertNull(numbers[unassigned.id], "expected an unassigned training to carry no number (AC TRAIN-02)")
    }

    @Test
    fun `numbering is per team — each team's history restarts at 1`() {
        val teamA = newId()
        val teamB = newId()
        val aFirst = training(teamA, day(10))
        val aSecond = training(teamA, day(24))
        val bOnly = training(teamB, day(17))

        val numbers = TrainingNumbering.numbersById(listOf(aFirst, aSecond, bOnly))

        assertEquals(1, numbers[aFirst.id], "expected team A's earliest training to be number 1")
        assertEquals(2, numbers[aSecond.id], "expected team A's later training to be number 2")
        assertEquals(1, numbers[bOnly.id], "expected team B's only training to restart at 1, not continue team A's sequence")
    }

    @Test
    fun `empty input returns an empty map`() {
        assertEquals(emptyMap(), TrainingNumbering.numbersById(emptyList()))
    }
}
