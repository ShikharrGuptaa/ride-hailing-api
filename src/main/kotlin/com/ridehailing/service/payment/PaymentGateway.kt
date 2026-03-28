package com.ridehailing.service.payment

import java.math.BigDecimal

data class PaymentOrderResponse(
  val orderId: String,
  val amount: BigDecimal,
  val currency: String,
  val status: String,
  val pspReference: String
)

data class PaymentStatusResponse(
  val orderId: String,
  val status: String,
  val pspReference: String,
  val amountPaid: BigDecimal?
)

interface PaymentGateway {

  fun createOrder(amount: BigDecimal, currency: String, receiptId: String): PaymentOrderResponse

  fun getOrderStatus(orderId: String): PaymentStatusResponse
}
