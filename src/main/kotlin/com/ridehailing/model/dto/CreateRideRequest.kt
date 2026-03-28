package com.ridehailing.model.dto

import jakarta.validation.constraints.NotNull

data class CreateRideRequest(
  @field:NotNull val riderId: java.util.UUID,
  @field:NotNull val pickupLat: Double,
  @field:NotNull val pickupLng: Double,
  val pickupAddress: String? = null,
  @field:NotNull val destinationLat: Double,
  @field:NotNull val destinationLng: Double,
  val destinationAddress: String? = null,
  @field:NotNull val vehicleTypeId: Int,
  val regionId: Int? = null
)
