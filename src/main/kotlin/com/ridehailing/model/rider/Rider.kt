package com.ridehailing.model.rider

import com.ridehailing.model.common.IdName
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Rider(
  val id: UUID? = null,
  val tenantId: UUID,
  val region: IdName? = null,
  val name: String,
  val phone: String,
  val email: String? = null,
  val rating: BigDecimal = BigDecimal("5.00"),
  val addDate: OffsetDateTime? = null,
  val updateDate: OffsetDateTime? = null,
  val deleteInfo: String? = null
)
