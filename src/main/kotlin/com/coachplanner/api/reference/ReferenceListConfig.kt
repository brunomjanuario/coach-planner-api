package com.coachplanner.api.reference

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Supplies one `ReferenceListService` instance per managed list. Spring
 * resolves each controller's constructor injection by the bean's concrete
 * generic type (`ReferenceListService<Competition>` vs
 * `ReferenceListService<Opponent>`), a long-standing, well-supported Spring
 * feature — not by bean name.
 */
@Configuration
class ReferenceListConfig(private val competitionRepository: CompetitionRepository) {

    @Bean
    fun competitionReferenceListService(): ReferenceListService<Competition> =
        ReferenceListService(
            repository = competitionRepository,
            newEntry = { ownerId, name -> Competition(ownerId = ownerId, name = name) },
        )
}
