package com.ridehailing.model.ride

import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.VehicleType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Ride(
  val id: UUID? = null,
  val tenantId: UUID? = null,
  val region: IdName? = null,
  val riderId: UUID? = null,
  val driverId: UUID? = null,
  val status: IdName? = IdName(RideStatus.REQUESTED.id),
  val pickupLat: Double = 0.0,
  val pickupLng: Double = 0.0,
  val pickupAddress: String? = null,
  val destinationLat: Double = 0.0,
  val destinationLng: Double = 0.0,
  val destinationAddress: String? = null,
  val vehicleType: IdName? = IdName(VehicleType.ECONOMY.id),
  val estimatedFare: BigDecimal? = null,
  val surgeMultiplier: BigDecimal = BigDecimal("1.00"),
  val idempotencyKey: String? = null,
  val requestedAt: OffsetDateTime? = null,
  val matchedAt: OffsetDateTime? = null,
  val startedAt: OffsetDateTime? = null,
  val completedAt: OffsetDateTime? = null,
  val cancelledAt: OffsetDateTime? = null,
  val addDate: OffsetDateTime? = null,
  val updateDate: OffsetDateTime? = null,
  val deleteInfo: String? = null
)
