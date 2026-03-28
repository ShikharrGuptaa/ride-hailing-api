package com.ridehailing.model

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Driver(
  val id: UUID? = null,
  val tenantId: UUID,
  val regionId: Int? = null,
  val name: String,
  val phone: String,
  val vehicleTypeId: Int,
  val licensePlate: String,
  val statusId: Int = DriverStatus.OFFLINE.id,
  val rating: BigDecimal = BigDecimal("5.00"),
  val addDate: OffsetDateTime? = null,
  val updateDate: OffsetDateTime? = null,
  val deleteInfo: String? = null
)

data class DriverCurrentLocation(
  val driverId: UUID,
  val lat: Double,
  val lng: Double,
  val updateDate: OffsetDateTime? = null
)

data class Rider(
  val id: UUID? = null,
  val tenantId: UUID,
  val regionId: Int? = null,
  val name: String,
  val phone: String,
  val email: String? = null,
  val rating: BigDecimal = BigDecimal("5.00"),
  val addDate: OffsetDateTime? = null,
  val updateDate: OffsetDateTime? = null,
  val deleteInfo: String? = null
)
