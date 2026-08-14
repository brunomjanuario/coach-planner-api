package com.coachplanner.api.training

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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** tasks.md T8: round-trips Training/Exercise against real Postgres 18, including a non-trivial diagram and a null one. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class TrainingPersistenceTest @Autowired constructor(
    private val trainingRepository: TrainingRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {

    private fun persistedOwner(): User =
        userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))

    @Test
    fun `an unassigned training round-trips with a null team id`() {
        val owner = persistedOwner()
        val saved = trainingRepository.saveAndFlush(
            Training(ownerId = owner.id, day = Instant.parse("2024-10-24T15:00:00Z"), durationMinutes = 90),
        )
        entityManager.clear()

        val reloaded = trainingRepository.findById(saved.id).orElseThrow()
        assertNull(reloaded.teamId)
        assertEquals(90, reloaded.durationMinutes)
        assertEquals(Instant.parse("2024-10-24T15:00:00Z"), reloaded.day)
    }

    @Test
    fun `exercises round-trip in insertion order via the order column`() {
        val owner = persistedOwner()
        val training = Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 90)
        training.exercises.addAll(
            listOf(
                Exercise(training = training, orderIndex = 0, description = "Corrida", durationMinutes = 10, repetitions = 1),
                Exercise(training = training, orderIndex = 1, description = "SSG", durationMinutes = 20, repetitions = 2),
                Exercise(training = training, orderIndex = 2, description = "Jogo", durationMinutes = 10, repetitions = 3),
            ),
        )
        val saved = trainingRepository.saveAndFlush(training)
        entityManager.clear()

        val reloaded = trainingRepository.findById(saved.id).orElseThrow()
        assertEquals(listOf("Corrida", "SSG", "Jogo"), reloaded.exercises.map { it.description })
    }

    @Test
    fun `exercises reload ordered by orderIndex, not by insertion order`() {
        // Deliberately inserted out of orderIndex order — proves the ordering is read via
        // @OrderBy(orderIndex ASC), not an incidental match with insertion/primary-key order.
        val owner = persistedOwner()
        val training = Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 90)
        training.exercises.addAll(
            listOf(
                Exercise(training = training, orderIndex = 2, description = "Jogo"),
                Exercise(training = training, orderIndex = 0, description = "Corrida"),
                Exercise(training = training, orderIndex = 1, description = "SSG"),
            ),
        )
        val saved = trainingRepository.saveAndFlush(training)
        entityManager.clear()

        val reloaded = trainingRepository.findById(saved.id).orElseThrow()
        assertEquals(listOf("Corrida", "SSG", "Jogo"), reloaded.exercises.map { it.description })
    }

    @Test
    fun `an exercise with a non-trivial diagram round-trips its shapes`() {
        val owner = persistedOwner()
        val training = Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 90)
        val diagram = mapOf(
            "v" to 1,
            "pitch" to "full",
            "shapes" to listOf(
                mapOf("kind" to "player-a", "x" to 0.25, "y" to 0.5),
                mapOf("kind" to "cone", "x" to 0.6, "y" to 0.3),
                mapOf("kind" to "arrow", "points" to listOf(listOf(0.1, 0.1), listOf(0.9, 0.9))),
            ),
        )
        training.exercises.add(Exercise(training = training, orderIndex = 0, description = "Passing drill", diagram = diagram))
        val saved = trainingRepository.saveAndFlush(training)
        entityManager.clear()

        val reloaded = trainingRepository.findById(saved.id).orElseThrow()
        val reloadedDiagram = reloaded.exercises.single().diagram!!
        assertEquals(1, reloadedDiagram["v"])
        assertEquals("full", reloadedDiagram["pitch"])
        @Suppress("UNCHECKED_CAST")
        val shapes = reloadedDiagram["shapes"] as List<Map<String, Any?>>
        assertEquals(3, shapes.size)
        assertEquals("player-a", shapes[0]["kind"])
        assertEquals(0.25, shapes[0]["x"])
    }

    @Test
    fun `an exercise with no diagram round-trips it as null, not an empty object`() {
        val owner = persistedOwner()
        val training = Training(ownerId = owner.id, day = Instant.now(), durationMinutes = 90)
        training.exercises.add(Exercise(training = training, orderIndex = 0, description = "Corrida"))
        val saved = trainingRepository.saveAndFlush(training)
        entityManager.clear()

        val reloaded = trainingRepository.findById(saved.id).orElseThrow()
        assertNull(reloaded.exercises.single().diagram)
    }
}
