package com.coachplanner.api.game

import com.coachplanner.api.common.OwnedRepository
import java.util.UUID

interface GameRepository : OwnedRepository<Game, UUID>
