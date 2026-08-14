package com.coachplanner.api.team

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TeamRepository : JpaRepository<Team, UUID>
