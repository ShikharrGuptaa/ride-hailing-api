package com.ridehailing.model.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class AcceptRideRequest(
  @field:NotNull val rideId: UUID
)
