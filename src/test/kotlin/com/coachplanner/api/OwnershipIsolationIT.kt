package com.coachplanner.api

import com.coachplanner.api.auth.User
import com.coachplanner.api.auth.UserRepository
import com.coachplanner.api.common.newId
import com.coachplanner.api.game.Game
import com.coachplanner.api.game.GameRepository
import com.coachplanner.api.reference.Competition
import com.coachplanner.api.reference.CompetitionRepository
import com.coachplanner.api.reference.Opponent
import com.coachplanner.api.reference.OpponentRepository
import com.coachplanner.api.standings.RivalRow
import com.coachplanner.api.standings.RivalRowRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import com.coachplanner.api.training.Training
import com.coachplanner.api.training.TrainingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** One entity per line — the label is what shows up in the test report. */
class OwnershipCase(
    val label: String,
    val create: (UUID) -> UUID,
    val find: (UUID, UUID) -> Any?,
) {
    override fun toString() = label
}

/**
 * tasks.md T21 / AC OWN-01: a reusable two-user fixture plus a parameterised
 * test that later phases extend one line at a time as each owned entity
 * lands (six entities exist as of this task — Player/Exercise/Card/Rating
 * have no owner_id of their own, reached only through an owned parent).
 *
 * This proves OwnedRepository.findByIdAndOwnerId directly, at the
 * repository layer — no domain controllers exist yet (Phase 4+ builds
 * those), so there is no HTTP-level 404-not-403 behavior to test until
 * then. This is the mechanism the later HTTP-level tests will rely on.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnershipIsolationIT @Autowired constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val trainingRepository: TrainingRepository,
    private val gameRepository: GameRepository,
    private val rivalRowRepository: RivalRowRepository,
    private val competitionRepository: CompetitionRepository,
    private val opponentRepository: OpponentRepository,
) {

    private lateinit var ownerA: User
    private lateinit var ownerB: User

    @BeforeEach
    fun createOwners() {
        ownerA = userRepository.saveAndFlush(User(email = "owner-a-${newId()}@example.com", name = "A", passwordHash = "hash"))
        ownerB = userRepository.saveAndFlush(User(email = "owner-b-${newId()}@example.com", name = "B", passwordHash = "hash"))
    }

    fun cases(): List<OwnershipCase> = listOf(
        OwnershipCase(
            "Team",
            create = { owner -> teamRepository.saveAndFlush(Team(ownerId = owner, name = "Sub-11")).id },
            find = { id, owner -> teamRepository.findByIdAndOwnerId(id, owner) },
        ),
        OwnershipCase(
            "Training",
            create = { owner -> trainingRepository.saveAndFlush(Training(ownerId = owner, day = Instant.now(), durationMinutes = 60)).id },
            find = { id, owner -> trainingRepository.findByIdAndOwnerId(id, owner) },
        ),
        OwnershipCase(
            "Game",
            create = { owner ->
                gameRepository.saveAndFlush(Game(ownerId = owner, opponent = "Benfica", date = Instant.now(), isHome = true)).id
            },
            find = { id, owner -> gameRepository.findByIdAndOwnerId(id, owner) },
        ),
        OwnershipCase(
            "RivalRow",
            create = { owner ->
                rivalRowRepository.saveAndFlush(
                    RivalRow(ownerId = owner, name = "Sporting", played = 0, won = 0, drawn = 0, lost = 0, goalsFor = 0, goalsAgainst = 0),
                ).id
            },
            find = { id, owner -> rivalRowRepository.findByIdAndOwnerId(id, owner) },
        ),
        OwnershipCase(
            "Competition",
            create = { owner -> competitionRepository.saveAndFlush(Competition(ownerId = owner, name = "District League")).id },
            find = { id, owner -> competitionRepository.findByIdAndOwnerId(id, owner) },
        ),
        OwnershipCase(
            "Opponent",
            create = { owner -> opponentRepository.saveAndFlush(Opponent(ownerId = owner, name = "Benfica")).id },
            find = { id, owner -> opponentRepository.findByIdAndOwnerId(id, owner) },
        ),
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `an entity is visible to its owner and invisible to a different user`(case: OwnershipCase) {
        val id = case.create(ownerA.id)

        assertNotNull(case.find(id, ownerA.id), "expected ${case.label} to be found by its real owner")
        assertNull(case.find(id, ownerB.id), "expected ${case.label} to be invisible to a different user")
    }
}
