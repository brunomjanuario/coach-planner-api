package com.coachplanner.api.team

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.auth.User
import com.coachplanner.api.auth.UserRepository
import com.coachplanner.api.common.newId
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * tasks.md T7: round-trips Team and Player against real Postgres 18 —
 * ddl-auto=validate passing is itself the proof the entity mappings match
 * V1__init.sql (design.md).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class TeamPersistenceTest @Autowired constructor(
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {

    private fun persistedOwner(): User =
        userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))

    @Test
    fun `a team round-trips with its owner, club, season and version defaulted`() {
        val owner = persistedOwner()
        val saved = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11", club = "Amadora", season = "23-24"))
        entityManager.clear()

        val reloaded = teamRepository.findById(saved.id).orElseThrow()
        assertEquals("Sub-11", reloaded.name)
        assertEquals("Amadora", reloaded.club)
        assertEquals("23-24", reloaded.season)
        assertEquals(owner.id, reloaded.ownerId)
        assertEquals(0, reloaded.version)
        assertNotNull(reloaded.createdAt)
    }

    @Test
    fun `a team with no club or season round-trips those fields as null`() {
        val owner = persistedOwner()
        val saved = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-19"))
        entityManager.clear()

        val reloaded = teamRepository.findById(saved.id).orElseThrow()
        assertNull(reloaded.club)
        assertNull(reloaded.season)
    }

    @Test
    fun `players are ordered by shirt number then name when the team is reloaded`() {
        val owner = persistedOwner()
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))

        playerRepository.saveAndFlush(Player(team = team, name = "Zed", shirtNumber = 3))
        playerRepository.saveAndFlush(Player(team = team, name = "Ana", shirtNumber = 3))
        playerRepository.saveAndFlush(Player(team = team, name = "João", shirtNumber = 1))
        entityManager.clear()

        val reloaded = teamRepository.findById(team.id).orElseThrow()
        assertEquals(listOf("João", "Ana", "Zed"), reloaded.players.map { it.name })
    }

    @Test
    fun `a player round-trips its position, stats and team association`() {
        val owner = persistedOwner()
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val saved = playerRepository.saveAndFlush(
            Player(
                team = team,
                name = "João",
                age = 15,
                shirtNumber = 9,
                position = PlayerPosition.ST,
                goals = 3,
                assists = 1,
                concededGoals = 0,
            ),
        )
        entityManager.clear()

        val reloaded = playerRepository.findById(saved.id).orElseThrow()
        assertEquals(PlayerPosition.ST, reloaded.position)
        assertEquals(15, reloaded.age)
        assertEquals(9, reloaded.shirtNumber)
        assertEquals(3, reloaded.goals)
        assertEquals(1, reloaded.assists)
        assertEquals(0, reloaded.concededGoals)
        assertEquals(team.id, reloaded.team.id)
    }

    @Test
    fun `a player round-trips with a null position and null shirt number`() {
        val owner = persistedOwner()
        val team = teamRepository.saveAndFlush(Team(ownerId = owner.id, name = "Sub-11"))
        val saved = playerRepository.saveAndFlush(Player(team = team, name = "Reserve"))
        entityManager.clear()

        val reloaded = playerRepository.findById(saved.id).orElseThrow()
        assertNull(reloaded.position)
        assertNull(reloaded.shirtNumber)
        assertNull(reloaded.age)
    }
}
