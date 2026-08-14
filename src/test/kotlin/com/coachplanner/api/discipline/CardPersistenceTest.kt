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
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals

/** tasks.md T9: round-trips Card, including both enum values against the native card_type type. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class CardPersistenceTest @Autowired constructor(
    private val cardRepository: CardRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val gameRepository: GameRepository,
    private val entityManager: EntityManager,
) {

    private fun fixturePlayerAndGame(): Pair<Player, Game> {
        val owner = userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val game = gameRepository.saveAndFlush(Game(ownerId = owner.id, opponent = "Benfica", date = Instant.now(), isHome = true))
        return player to game
    }

    @Test
    fun `a yellow card round-trips`() {
        val (player, game) = fixturePlayerAndGame()
        val saved = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = game.id, type = CardType.yellow))
        entityManager.clear()

        val reloaded = cardRepository.findById(saved.id).orElseThrow()
        assertEquals(CardType.yellow, reloaded.type)
        assertEquals(player.id, reloaded.playerId)
        assertEquals(game.id, reloaded.gameId)
    }

    @Test
    fun `a red card round-trips`() {
        val (player, game) = fixturePlayerAndGame()
        val saved = cardRepository.saveAndFlush(Card(playerId = player.id, gameId = game.id, type = CardType.red))
        entityManager.clear()

        val reloaded = cardRepository.findById(saved.id).orElseThrow()
        assertEquals(CardType.red, reloaded.type)
    }
}
