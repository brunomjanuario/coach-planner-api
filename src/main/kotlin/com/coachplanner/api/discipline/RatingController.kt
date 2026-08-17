package com.coachplanner.api.discipline

import com.coachplanner.api.common.CurrentUser
import com.coachplanner.api.discipline.dto.RatingDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/ratings")
class RatingController(private val ratingService: RatingService) {

    @GetMapping
    fun getAll(
        @CurrentUser ownerId: UUID,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) eventId: UUID?,
        @RequestParam(required = false) playerId: UUID?,
    ): List<RatingDto> = ratingService.getAll(ownerId, eventType, eventId, playerId)
}
