package com.coachplanner.api.reference

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CompetitionRepository : JpaRepository<Competition, UUID>
