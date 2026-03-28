package com.ridehailing.model.driver

import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.DriverStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Driver(
  val id: UUID? = null,
  val tenantId: UUID? = null,
  val region: IdName? = null,
  val name: String? = null,
  val phone: String? = null,
  val vehicleType: IdName? = null,
  val licensePlate: String? = null,
  val status: IdName? = IdName(DriverStatus.OFFLINE.id),
  val rating: BigDecimal? = BigDecimal("5.00"),
  val addDate: OffsetDateTime? = null,
  val updateDate: OffsetDateTime? = null,
  val deleteInfo: String? = null
)
