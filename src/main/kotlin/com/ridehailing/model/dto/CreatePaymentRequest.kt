package com.ridehailing.model.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CreatePaymentRequest(
  @field:NotNull val tripId: UUID,
  @field:NotNull val paymentMethodId: Int
)
