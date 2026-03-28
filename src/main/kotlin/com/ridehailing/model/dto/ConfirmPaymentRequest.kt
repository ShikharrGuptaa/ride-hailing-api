package com.ridehailing.model.dto

import jakarta.validation.constraints.NotBlank

data class ConfirmPaymentRequest(
  @field:NotBlank val razorpayPaymentId: String
)
