package com.ridehailing.model.payment

import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.PaymentStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Payment(
  val id: UUID? = null,
  val tenantId: UUID,
  val tripId: UUID,
  val riderId: UUID,
  val amount: BigDecimal,
  val currency: String = "INR",
  val status: IdName? = IdName(PaymentStatus.PENDING.id),
  val paymentMethod: IdName? = null,
  val pspReference: String? = null,
  val idempotencyKey: String? = null,
  val addDate: OffsetDateTime? = null,
  val updateDate: OffsetDateTime? = null,
  val deleteInfo: String? = null
)
