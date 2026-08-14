package com.coachplanner.api.training

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TrainingRepository : JpaRepository<Training, UUID>
