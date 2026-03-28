package com.ridehailing.service.payment

import com.razorpay.Order
import com.razorpay.RazorpayClient
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class RazorpayPaymentGateway(
  @Value("\${razorpay.key-id}") private val keyId: String,
  @Value("\${razorpay.key-secret}") private val keySecret: String
) : PaymentGateway {

  private val log = LoggerFactory.getLogger(RazorpayPaymentGateway::class.java)

  private val client: RazorpayClient by lazy {
    RazorpayClient(keyId, keySecret)
  }

  override fun createOrder(amount: BigDecimal, currency: String, receiptId: String): PaymentOrderResponse {
    log.info("createOrder - Creating Razorpay order: amount=$amount, currency=$currency, receipt=$receiptId")

    val orderRequest = JSONObject()
    orderRequest.put("amount", amount.multiply(BigDecimal(100)).toInt()) // Razorpay expects paise
    orderRequest.put("currency", currency)
    orderRequest.put("receipt", receiptId)

    val order: Order = client.orders.create(orderRequest)

    val response = PaymentOrderResponse(
      orderId = order.get<String>("id"),
      amount = amount,
      currency = currency,
      status = order.get<String>("status"),
      pspReference = order.get<String>("id")
    )

    log.info("createOrder - Razorpay order created: ${response.orderId}, status: ${response.status}")
    return response
  }

  override fun getOrderStatus(orderId: String): PaymentStatusResponse {
    log.info("getOrderStatus - Fetching Razorpay order status: $orderId")

    val order: Order = client.orders.fetch(orderId)

    return PaymentStatusResponse(
      orderId = order.get<String>("id"),
      status = order.get<String>("status"),
      pspReference = order.get<String>("id"),
      amountPaid = BigDecimal(order.get<Int>("amount_paid")).divide(BigDecimal(100))
    )
  }
}
