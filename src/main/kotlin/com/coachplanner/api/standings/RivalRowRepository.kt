package com.coachplanner.api.standings

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RivalRowRepository : JpaRepository<RivalRow, UUID>
