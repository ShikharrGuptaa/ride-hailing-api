package com.ridehailing.model.driver

import java.time.OffsetDateTime
import java.util.UUID

data class DriverCurrentLocation(
  val driverId: UUID,
  val lat: Double,
  val lng: Double,
  val updateDate: OffsetDateTime? = null
)
