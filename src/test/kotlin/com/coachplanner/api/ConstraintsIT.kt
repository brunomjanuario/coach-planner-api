package com.coachplanner.api

import com.coachplanner.api.auth.User
import com.coachplanner.api.auth.UserRepository
import com.coachplanner.api.common.newId
import com.coachplanner.api.discipline.Rating
import com.coachplanner.api.discipline.RatingRepository
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.reference.Competition
import com.coachplanner.api.reference.CompetitionRepository
import com.coachplanner.api.standings.RivalRow
import com.coachplanner.api.standings.RivalRowRepository
import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import com.coachplanner.api.training.Training
import com.coachplanner.api.training.TrainingRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

/**
 * tasks.md T10 / design.md's cascade matrix: attempts each illegal write
 * directly against the repository — bypassing any service-layer validation
 * that doesn't exist yet — and confirms the database itself rejects it.
 * No production code in this task; it is the safety net for the schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class ConstraintsIT @Autowired constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val trainingRepository: TrainingRepository,
    private val gameRepository: GameRepository,
    private val ratingRepository: RatingRepository,
    private val rivalRowRepository: RivalRowRepository,
    private val competitionRepository: CompetitionRepository,
) {

    private fun persistedOwner(): User =
        userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))

    private fun persistedPlayer(owner: User): Player {
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        return playerRepository.saveAndFlush(Player(team = team, name = "João"))
    }

    // ─── ratings.exactly_one_event ─────────────────────────────────────

    @Test
    fun `a rating with both trainingId and gameId set is rejected`() {
        val owner = persistedOwner()
        val player = persistedPlayer(owner)
        val training = trainingRepository.saveAndFlush(Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 90))
        val game = gameRepository.saveAndFlush(Game(ownerId = owner.id, opponent = "Benfica", date = Instant.now(), isHome = true))

        assertThrows<DataIntegrityViolationException> {
            ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = training.id, gameId = game.id, value = 5))
        }
    }

    @Test
    fun `a rating with neither trainingId nor gameId set is rejected`() {
        val owner = persistedOwner()
        val player = persistedPlayer(owner)

        assertThrows<DataIntegrityViolationException> {
            ratingRepository.saveAndFlush(Rating(playerId = player.id, value = 5))
        }
    }

    @Test
    fun `two ratings for the same player and game are rejected`() {
        val owner = persistedOwner()
        val player = persistedPlayer(owner)
        val game = gameRepository.saveAndFlush(Game(ownerId = owner.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = game.id, value = 5))

        assertThrows<DataIntegrityViolationException> {
            ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = game.id, value = 8))
        }
    }

    // ─── games.scores_recorded_together ────────────────────────────────

    @Test
    fun `a game with only one score recorded is rejected`() {
        val owner = persistedOwner()

        assertThrows<DataIntegrityViolationException> {
            gameRepository.saveAndFlush(
                Game(ownerId = owner.id, opponent = "Benfica", date = Instant.now(), isHome = true, usScore = 3, themScore = null),
            )
        }
    }

    // ─── standings_rivals.results_sum_to_played ────────────────────────

    @Test
    fun `a rival row whose won+drawn+lost does not equal played is rejected`() {
        val owner = persistedOwner()

        assertThrows<DataIntegrityViolationException> {
            rivalRowRepository.saveAndFlush(
                RivalRow(ownerId = owner.id, name = "Sporting", played = 10, won = 6, drawn = 2, lost = 1, goalsFor = 0, goalsAgainst = 0),
            )
        }
    }

    // ─── uq_competitions_owner_name (case-insensitive) ─────────────────

    @Test
    fun `two competitions differing only in case are rejected`() {
        val owner = persistedOwner()
        competitionRepository.saveAndFlush(Competition(ownerId = owner.id, name = "District League"))

        assertThrows<DataIntegrityViolationException> {
            competitionRepository.saveAndFlush(Competition(ownerId = owner.id, name = "district league"))
        }
    }
}
