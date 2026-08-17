package com.coachplanner.api.standings

import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/** tasks.md T37 / AC STAND-11, STAND-12. Pure logic — no Spring context. */
class StandingsCalculatorTest {

    private val owner = newId()

    private fun game(us: Int?, them: Int?) =
        Game(ownerId = owner, opponent = "X", date = Instant.now(), isHome = true, usScore = us, themScore = them)

    @Test
    fun `only played games count — a scheduled fixture contributes nothing`() {
        val row = StandingsCalculator.computeOurRow("Sub-11", listOf(game(3, 1), game(null, null)))

        assertEquals(1, row.played, "expected the scheduled game to be excluded from played")
        assertEquals(3, row.goalsFor)
        assertEquals(1, row.goalsAgainst)
    }

    @Test
    fun `points equal won times 3 plus drawn`() {
        val row = StandingsCalculator.computeOurRow("Sub-11", listOf(game(3, 1), game(1, 1), game(0, 2)))

        assertEquals(1, row.won)
        assertEquals(1, row.drawn)
        assertEquals(1, row.lost)
        assertEquals(1 * 3 + 1, row.points, "expected points = won*3 + drawn")
    }

    @Test
    fun `a team with no played games yields an all-zero row, present, never absent`() {
        val row = StandingsCalculator.computeOurRow("Sub-11", emptyList())

        assertEquals("Sub-11", row.name)
        assertEquals(0, row.played)
        assertEquals(0, row.won)
        assertEquals(0, row.drawn)
        assertEquals(0, row.lost)
        assertEquals(0, row.goalsFor)
        assertEquals(0, row.goalsAgainst)
        assertEquals(0, row.goalDifference)
        assertEquals(0, row.points)
    }

    @Test
    fun `a rival row is normalized to the same shape with points and goalDifference derived`() {
        val rival = RivalRow(ownerId = owner, name = "Benfica", played = 3, won = 2, drawn = 1, lost = 0, goalsFor = 6, goalsAgainst = 2)

        val row = StandingsCalculator.fromRivalRow(rival)

        assertEquals(4, row.goalDifference, "expected goalDifference = goalsFor - goalsAgainst")
        assertEquals(2 * 3 + 1, row.points, "expected points = won*3 + drawn")
        assertEquals(false, row.isOurs)
    }

    private fun rival(name: String, won: Int, drawn: Int, lost: Int, goalsFor: Int = 0, goalsAgainst: Int = 0) =
        RivalRow(
            ownerId = owner,
            name = name,
            played = won + drawn + lost,
            won = won,
            drawn = drawn,
            lost = lost,
            goalsFor = goalsFor,
            goalsAgainst = goalsAgainst,
        )

    @Test
    fun `sort breaks ties by points, then goal difference, then goals for, then name`() {
        // Row A: highest points, wins outright.
        val topOnPoints = StandingsCalculator.fromRivalRow(rival("Top", won = 5, drawn = 0, lost = 0))
        // Rows B/C: same points as each other, tied lower than A, separated by goal difference.
        val higherGoalDifference = StandingsCalculator.fromRivalRow(rival("HigherGD", won = 3, drawn = 0, lost = 2, goalsFor = 10, goalsAgainst = 2))
        val lowerGoalDifference = StandingsCalculator.fromRivalRow(rival("LowerGD", won = 3, drawn = 0, lost = 2, goalsFor = 5, goalsAgainst = 4))
        // Rows D/E: same points and same goal difference, separated by goals for.
        val moreGoalsFor = StandingsCalculator.fromRivalRow(rival("MoreGoals", won = 1, drawn = 1, lost = 3, goalsFor = 8, goalsAgainst = 6))
        val fewerGoalsFor = StandingsCalculator.fromRivalRow(rival("FewerGoals", won = 1, drawn = 1, lost = 3, goalsFor = 4, goalsAgainst = 2))
        // Rows F/G: same points, same goal difference, same goals for — separated by name.
        val nameB = StandingsCalculator.fromRivalRow(rival("Beta", won = 0, drawn = 0, lost = 5))
        val nameA = StandingsCalculator.fromRivalRow(rival("Alpha", won = 0, drawn = 0, lost = 5))

        val sorted = StandingsCalculator.sort(
            listOf(nameB, moreGoalsFor, lowerGoalDifference, topOnPoints, fewerGoalsFor, nameA, higherGoalDifference),
        )

        assertEquals(
            listOf("Top", "HigherGD", "LowerGD", "MoreGoals", "FewerGoals", "Alpha", "Beta"),
            sorted.map { it.name },
        )
    }
}
