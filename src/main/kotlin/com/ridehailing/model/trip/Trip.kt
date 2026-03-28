package com.ridehailing.model.trip

import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.TripStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Trip(
  val id: UUID? = null,
  val tenantId: UUID? = null,
  val region: IdName? = null,
  val rideId: UUID? = null,
  val driverId: UUID? = null,
  val riderId: UUID? = null,
  val status: IdName? = IdName(TripStatus.IN_PROGRESS.id),
  val startLat: Double = 0.0,
  val startLng: Double = 0.0,
  val endLat: Double? = null,
  val endLng: Double? = null,
  val distanceKm: BigDecimal? = null,
  val durationMinutes: BigDecimal? = null,
  val baseFare: BigDecimal? = null,
  val distanceFare: BigDecimal? = null,
  val timeFare: BigDecimal? = null,
  val surgeMultiplier: BigDecimal = BigDecimal("1.00"),
  val totalFare: BigDecimal? = null,
  val startedAt: OffsetDateTime? = null,
  val endedAt: OffsetDateTime? = null,
  val addDate: OffsetDateTime? = null,
  val updateDate: OffsetDateTime? = null,
  val deleteInfo: String? = null
)
