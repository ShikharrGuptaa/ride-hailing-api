package com.ridehailing.controller

import com.ridehailing.model.common.ApiResponse
import com.ridehailing.model.payment.Payment
import com.ridehailing.model.dto.ConfirmPaymentRequest
import com.ridehailing.model.dto.CreatePaymentRequest
import com.ridehailing.service.PaymentService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/payments")
class PaymentController(
  private val paymentService: PaymentService
) {

  private val log = LoggerFactory.getLogger(PaymentController::class.java)

  @Operation(summary = "Create payment order (returns Razorpay order ID)")
  @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  fun createPayment(@Valid @RequestBody request: CreatePaymentRequest): ApiResponse<Payment> {
    log.info("createPayment - POST /payments for trip: ${request.tripId}")
    val payment = paymentService.createPayment(request)
    return ApiResponse.ok(payment, "Payment order created")
  }

  @Operation(summary = "Confirm payment after Razorpay checkout")
  @PostMapping(value = ["/{id}/confirm"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun confirmPayment(
    @PathVariable id: UUID,
    @Valid @RequestBody request: ConfirmPaymentRequest
  ): ApiResponse<Payment> {
    log.info("confirmPayment - POST /payments/$id/confirm")
    val payment = paymentService.confirmPayment(id, request.razorpayPaymentId)
    return ApiResponse.ok(payment, "Payment confirmed")
  }

  @Operation(summary = "Get payment details")
  @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getPayment(@PathVariable id: UUID): ApiResponse<Payment> {
    log.info("getPayment - GET /payments/$id")
    return ApiResponse.ok(paymentService.getPayment(id))
  }
}
