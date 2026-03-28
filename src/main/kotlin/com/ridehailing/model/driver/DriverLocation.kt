package com.ridehailing.model.driver

import java.time.OffsetDateTime
import java.util.UUID

data class DriverLocation(
  val id: Long? = null,
  val driverId: UUID,
  val lat: Double,
  val lng: Double,
  val recordedAt: OffsetDateTime? = null
)
