package com.ridehailing.model.enums

enum class PaymentStatus(val id: Int) {
  PENDING(401), PROCESSING(402), COMPLETED(403), FAILED(404), REFUNDED(405)
}
