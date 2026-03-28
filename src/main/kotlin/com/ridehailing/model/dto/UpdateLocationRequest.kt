package com.ridehailing.model.dto

import jakarta.validation.constraints.NotNull

data class UpdateLocationRequest(
  @field:NotNull val lat: Double,
  @field:NotNull val lng: Double
)
