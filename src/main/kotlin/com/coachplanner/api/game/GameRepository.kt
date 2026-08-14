package com.coachplanner.api.game

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GameRepository : JpaRepository<Game, UUID>
