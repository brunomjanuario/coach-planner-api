package com.coachplanner.api

import com.coachplanner.api.auth.User
import com.coachplanner.api.auth.UserRepository
import com.coachplanner.api.common.newId
import com.coachplanner.api.discipline.Card
import com.coachplanner.api.discipline.CardRepository
import com.coachplanner.api.discipline.CardType
import com.coachplanner.api.discipline.Rating
import com.coachplanner.api.discipline.RatingRepository
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import com.coachplanner.api.training.Exercise
import com.coachplanner.api.training.Training
import com.coachplanner.api.training.TrainingRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * tasks.md T10 / design.md's cascade matrix. Deletes go through raw SQL
 * (JdbcTemplate), not the JPA entity graph — Team.players is configured
 * with Hibernate's own cascade=ALL, which would produce the same observable
 * result regardless of whether the database's FK action exists. Only a raw
 * SQL delete proves the claim design.md actually makes: "enforced by FK
 * actions in the schema... holds for every write path including manual
 * SQL" (AD-107).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class CascadeIT @Autowired constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val trainingRepository: TrainingRepository,
    private val gameRepository: GameRepository,
    private val cardRepository: CardRepository,
    private val ratingRepository: RatingRepository,
    private val jdbc: JdbcTemplate,
    private val entityManager: EntityManager,
) {

    private fun persistedOwner(): User =
        userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))

    private fun exerciseCount(trainingId: UUID): Int =
        jdbc.queryForObject("SELECT count(*) FROM exercises WHERE training_id = ?", Int::class.java, trainingId)!!

    @Test
    fun `deleting a team deletes its players' cards and ratings, and nulls team_id on its trainings and games`() {
        val owner = persistedOwner()
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val training = trainingRepository.saveAndFlush(Training(ownerId = owner.id, teamId = team.id, day = Instant.now(), durationMinutes = 90))
        val game = gameRepository.saveAndFlush(Game(ownerId = owner.id, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        val card = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = game.id, type = CardType.yellow))
        val rating = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = training.id, value = 7))

        jdbc.update("DELETE FROM teams WHERE id = ?", team.id)
        entityManager.clear() // Hibernate's persistence context doesn't know about this out-of-band SQL delete

        assertTrue(playerRepository.findById(player.id).isEmpty, "expected the player to be deleted")
        assertTrue(cardRepository.findById(card.id).isEmpty, "expected the player's card to be deleted")
        assertTrue(ratingRepository.findById(rating.id).isEmpty, "expected the player's rating to be deleted")

        val reloadedTraining = trainingRepository.findById(training.id).orElseThrow()
        assertNull(reloadedTraining.teamId, "expected the training to survive with team_id nulled, not deleted")
        val reloadedGame = gameRepository.findById(game.id).orElseThrow()
        assertNull(reloadedGame.teamId, "expected the game to survive with team_id nulled, not deleted")
    }

    /** tasks.md T42 — the one entity-type cascade CascadeIT hadn't proven standalone; T10 only covered it transitively through a team delete. */
    @Test
    fun `deleting a player deletes only their own cards and ratings`() {
        val owner = persistedOwner()
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val game = gameRepository.saveAndFlush(Game(ownerId = owner.id, teamId = team.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        val training = trainingRepository.saveAndFlush(Training(ownerId = owner.id, teamId = team.id, day = Instant.now(), durationMinutes = 90))

        val deletedPlayer = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val cardOnDeletedPlayer = cardRepository.saveAndFlush(Card(playerId = deletedPlayer.id, gameId = game.id, type = CardType.yellow))
        val ratingOnDeletedPlayer = ratingRepository.saveAndFlush(Rating(playerId = deletedPlayer.id, trainingId = training.id, value = 6))

        val survivingPlayer = playerRepository.saveAndFlush(Player(team = team, name = "Pedro"))
        val cardOnSurvivingPlayer = cardRepository.saveAndFlush(Card(playerId = survivingPlayer.id, gameId = game.id, type = CardType.red))
        val ratingOnSurvivingPlayer = ratingRepository.saveAndFlush(Rating(playerId = survivingPlayer.id, trainingId = training.id, value = 3))

        jdbc.update("DELETE FROM players WHERE id = ?", deletedPlayer.id)
        entityManager.clear()

        assertTrue(cardRepository.findById(cardOnDeletedPlayer.id).isEmpty, "expected the deleted player's card to be gone")
        assertTrue(ratingRepository.findById(ratingOnDeletedPlayer.id).isEmpty, "expected the deleted player's rating to be gone")
        assertTrue(cardRepository.findById(cardOnSurvivingPlayer.id).isPresent, "expected the other player's card to survive")
        assertTrue(ratingRepository.findById(ratingOnSurvivingPlayer.id).isPresent, "expected the other player's rating to survive")
        assertTrue(teamRepository.findById(team.id).isPresent, "the team itself must not be touched by a player delete")
        assertTrue(gameRepository.findById(game.id).isPresent, "the game itself must not be touched by a player delete")
    }

    @Test
    fun `deleting a game deletes only its own cards and ratings`() {
        val owner = persistedOwner()
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))

        val deletedGame = gameRepository.saveAndFlush(Game(ownerId = owner.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        val cardOnDeletedGame = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = deletedGame.id, type = CardType.yellow))
        val ratingOnDeletedGame = ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = deletedGame.id, value = 4))

        val survivingGame = gameRepository.saveAndFlush(Game(ownerId = owner.id, opponent = "Sporting", date = Instant.now(), isHome = false))
        val cardOnSurvivingGame = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = survivingGame.id, type = CardType.red))
        val ratingOnSurvivingGame = ratingRepository.saveAndFlush(Rating(playerId = player.id, gameId = survivingGame.id, value = 9))

        jdbc.update("DELETE FROM games WHERE id = ?", deletedGame.id)
        entityManager.clear()

        assertTrue(cardRepository.findById(cardOnDeletedGame.id).isEmpty, "expected the deleted game's card to be gone")
        assertTrue(ratingRepository.findById(ratingOnDeletedGame.id).isEmpty, "expected the deleted game's rating to be gone")
        assertTrue(cardRepository.findById(cardOnSurvivingGame.id).isPresent, "expected the other game's card to survive")
        assertTrue(ratingRepository.findById(ratingOnSurvivingGame.id).isPresent, "expected the other game's rating to survive")
        assertTrue(playerRepository.findById(player.id).isPresent, "the player themselves must not be touched by a game delete")
    }

    @Test
    fun `deleting a training deletes its exercises and its ratings, and nothing else`() {
        val owner = persistedOwner()
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))

        val deletedTraining = Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 90)
        deletedTraining.exercises.add(Exercise(training = deletedTraining, orderIndex = 0, description = "Corrida"))
        deletedTraining.exercises.add(Exercise(training = deletedTraining, orderIndex = 1, description = "SSG"))
        trainingRepository.saveAndFlush(deletedTraining)
        val ratingOnDeletedTraining = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = deletedTraining.id, value = 6))

        val survivingTraining = trainingRepository.saveAndFlush(Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 60))
        val ratingOnSurvivingTraining = ratingRepository.saveAndFlush(Rating(playerId = player.id, trainingId = survivingTraining.id, value = 3))

        assertEquals(2, exerciseCount(deletedTraining.id))

        jdbc.update("DELETE FROM trainings WHERE id = ?", deletedTraining.id)
        entityManager.clear()

        assertEquals(0, exerciseCount(deletedTraining.id), "expected the deleted training's exercises to be gone")
        assertTrue(ratingRepository.findById(ratingOnDeletedTraining.id).isEmpty, "expected the deleted training's rating to be gone")
        assertTrue(ratingRepository.findById(ratingOnSurvivingTraining.id).isPresent, "expected the other training's rating to survive")
        assertTrue(trainingRepository.findById(survivingTraining.id).isPresent, "the other training must not be touched")
        assertTrue(playerRepository.findById(player.id).isPresent, "the player themselves must not be touched by a training delete")
    }
}
