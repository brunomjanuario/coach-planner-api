package com.coachplanner.api.reference.dto

import jakarta.validation.constraints.NotBlank

/** Shared by create (`POST`) and rename (`PATCH`) — both bodies are just `{ "name": "..." }`. */
data class ReferenceEntryRequest(
    @field:NotBlank(message = "must not be blank")
    val name: String,
)
