package com.coachplanner.api.discipline

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.auth.User
import com.coachplanner.api.auth.UserRepository
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import com.coachplanner.api.training.Training
import com.coachplanner.api.training.TrainingRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** tasks.md T9 / AD-106: a rating with a training and a rating with a game — never both, never neither (DB-enforced). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class RatingPersistenceTest @Autowired constructor(
    private val ratingRepository: RatingRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val trainingRepository: TrainingRepository,
    private val gameRepository: GameRepository,
    private val entityManager: EntityManager,
) {

    private fun fixturePlayer(): Pair<User, Player> {
        val owner = userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        return owner to player
    }

    @Test
    fun `a training rating round-trips with a null gameId`() {
        val (owner, player) = fixturePlayer()
        val training = trainingRepository.saveAndFlush(Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 90))
        val saved = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = training.id, value = 7))
        entityManager.clear()

        val reloaded = ratingRepository.findById(saved.id).orElseThrow()
        assertEquals(training.id, reloaded.trainingId)
        assertNull(reloaded.gameId)
        assertEquals(7.toShort(), reloaded.value)
    }

    @Test
    fun `a game rating round-trips with a null trainingId`() {
        val (owner, player) = fixturePlayer()
        val game = gameRepository.saveAndFlush(Game(ownerId = owner.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        val saved = ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = game.id, value = 0))
        entityManager.clear()

        val reloaded = ratingRepository.findById(saved.id).orElseThrow()
        assertEquals(game.id, reloaded.gameId)
        assertNull(reloaded.trainingId)
        assertEquals(0.toShort(), reloaded.value, "a rating of exactly 0 is a real value, not treated as absent")
    }
}
