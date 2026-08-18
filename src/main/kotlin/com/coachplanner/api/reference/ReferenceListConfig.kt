package com.coachplanner.api.reference

import com.coachplanner.api.game.GameRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Supplies one `ReferenceListService` instance per managed list. Spring
 * resolves each controller's constructor injection by the bean's concrete
 * generic type (`ReferenceListService<Competition>` vs
 * `ReferenceListService<Opponent>`), a long-standing, well-supported Spring
 * feature — not by bean name. Each `@Bean` closes over its own cascade
 * function, the one place the two lists actually differ (AD-104).
 */
@Configuration
class ReferenceListConfig(
    private val competitionRepository: CompetitionRepository,
    private val opponentRepository: OpponentRepository,
    private val gameRepository: GameRepository,
) {

    @Bean
    fun competitionReferenceListService(): ReferenceListService<Competition> =
        ReferenceListService(
            repository = competitionRepository,
            newEntry = { ownerId, name -> Competition(ownerId = ownerId, name = name) },
            cascadeRename = { ownerId, oldName, newName ->
                gameRepository.findAllByOwnerIdAndCompetitionIgnoreCase(ownerId, oldName).forEach {
                    it.competition = newName
                    gameRepository.saveAndFlush(it)
                }
            },
        )

    @Bean
    fun opponentReferenceListService(): ReferenceListService<Opponent> =
        ReferenceListService(
            repository = opponentRepository,
            newEntry = { ownerId, name -> Opponent(ownerId = ownerId, name = name) },
            cascadeRename = { ownerId, oldName, newName ->
                gameRepository.findAllByOwnerIdAndOpponentIgnoreCase(ownerId, oldName).forEach {
                    it.opponent = newName
                    gameRepository.saveAndFlush(it)
                }
            },
        )
}
