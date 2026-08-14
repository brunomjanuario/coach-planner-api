package com.coachplanner.api

import com.coachplanner.api.auth.JwtService
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
import com.coachplanner.api.team.Player
import com.coachplanner.api.team.PlayerRepository
import com.coachplanner.api.team.Team
import com.coachplanner.api.team.TeamRepository
import com.coachplanner.api.training.Training
import com.coachplanner.api.training.TrainingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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
 * tasks.md T21 / AC OWN-01 (repository-level, one line per owned entity)
 * and T26 / AC TEAM-02 (HTTP-level, now that TeamController/PlayerController
 * exist): user B must receive 404 — never 403 — reading, patching or
 * deleting user A's team or anything beneath it. @SpringBootTest +
 * @AutoConfigureMockMvc replaces the original @DataJpaTest slice from T21
 * so both kinds of check can live in one file, per tasks.md's own file list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnershipIsolationIT @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
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

    private fun tokenFor(user: User) = "Bearer ${jwtService.issueAccessToken(user.id)}"

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

    @Test
    fun `user B gets 404, never 403, reading, patching and deleting user A's team`() {
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerA.id, name = "A's Team"))
        val tokenB = tokenFor(ownerB)

        mockMvc.perform(get("/api/v1/teams/${team.id}").header(HttpHeaders.AUTHORIZATION, tokenB))
            .andExpect(status().isNotFound)
        mockMvc.perform(
            patch("/api/v1/teams/${team.id}")
                .header(HttpHeaders.AUTHORIZATION, tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Hijacked"}"""),
        ).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/v1/teams/${team.id}").header(HttpHeaders.AUTHORIZATION, tokenB))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `user B gets 404, never 403, on every player path beneath user A's team`() {
        val team = teamRepository.saveAndFlush(Team(ownerId = ownerA.id, name = "A's Team"))
        val player = playerRepository.saveAndFlush(Player(team = team, name = "João"))
        val tokenB = tokenFor(ownerB)

        mockMvc.perform(get("/api/v1/teams/${team.id}/players").header(HttpHeaders.AUTHORIZATION, tokenB))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/teams/${team.id}/players/${player.id}").header(HttpHeaders.AUTHORIZATION, tokenB))
            .andExpect(status().isNotFound)
        mockMvc.perform(
            post("/api/v1/teams/${team.id}/players")
                .header(HttpHeaders.AUTHORIZATION, tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Intruder"}"""),
        ).andExpect(status().isNotFound)
        mockMvc.perform(
            patch("/api/v1/teams/${team.id}/players/${player.id}")
                .header(HttpHeaders.AUTHORIZATION, tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Hijacked"}"""),
        ).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/v1/teams/${team.id}/players/${player.id}").header(HttpHeaders.AUTHORIZATION, tokenB))
            .andExpect(status().isNotFound)
    }
}
