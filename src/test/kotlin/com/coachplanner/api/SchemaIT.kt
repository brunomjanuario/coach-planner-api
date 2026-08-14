package com.coachplanner.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * tasks.md T6 / AC DATA-01…06: proves V1__init.sql actually creates the
 * schema design.md specifies — table existence, the three named CHECK
 * constraints, and every FK's ON DELETE action — against a real, freshly
 * migrated Postgres 18, not by re-reading the SQL file.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class SchemaIT @Autowired constructor(private val jdbc: JdbcTemplate) {

    private val expectedTables = setOf(
        "users", "refresh_tokens", "teams", "players", "trainings", "exercises",
        "games", "standings_rivals", "cards", "ratings", "competitions", "opponents",
    )

    private fun existingTables(): Set<String> =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String::class.java,
        ).filterNotNull().toSet()

    private fun checkConstraintExists(name: String): Boolean =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_constraint WHERE conname = ? AND contype = 'c'",
            Int::class.java,
            name,
        )!! == 1

    /** confdeltype: 'c' = CASCADE, 'n' = SET NULL, 'a' = NO ACTION, 'r' = RESTRICT, 'd' = SET DEFAULT. */
    private fun fkDeleteActions(): Map<String, Char> {
        val rows = jdbc.queryForList(
            """
            SELECT conrelid::regclass::text AS tbl, a.attname AS col, c.confdeltype AS action
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
            WHERE c.contype = 'f'
            """.trimIndent(),
        )
        return rows.associate { "${it["tbl"]}.${it["col"]}" to (it["action"] as String).single() }
    }

    @Test
    fun `every table from design_md exists`() {
        val actual = existingTables()
        for (table in expectedTables) {
            assertTrue(table in actual, "expected table '$table' to exist, found: $actual")
        }
    }

    @Test
    fun `the three named CHECK constraints exist`() {
        assertTrue(checkConstraintExists("exactly_one_event"), "ratings.exactly_one_event missing")
        assertTrue(checkConstraintExists("scores_recorded_together"), "games.scores_recorded_together missing")
        assertTrue(checkConstraintExists("results_sum_to_played"), "standings_rivals.results_sum_to_played missing")
    }

    @Test
    fun `every foreign key has the ON DELETE action the cascade matrix requires`() {
        val actions = fkDeleteActions()

        val expected = mapOf(
            "refresh_tokens.user_id" to 'c',
            "teams.owner_id" to 'c',
            "players.team_id" to 'c',
            "trainings.owner_id" to 'c',
            "trainings.team_id" to 'n',
            "exercises.training_id" to 'c',
            "games.owner_id" to 'c',
            "games.team_id" to 'n',
            "standings_rivals.owner_id" to 'c',
            "cards.player_id" to 'c',
            "cards.game_id" to 'c',
            "ratings.player_id" to 'c',
            "ratings.training_id" to 'c',
            "ratings.game_id" to 'c',
            "competitions.owner_id" to 'c',
            "opponents.owner_id" to 'c',
        )

        for ((key, expectedAction) in expected) {
            assertEquals(expectedAction, actions[key], "expected $key to have delete action '$expectedAction', found ${actions[key]}")
        }
    }
}
