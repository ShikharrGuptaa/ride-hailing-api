package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.mapper.PaymentMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.PaymentMethod
import com.ridehailing.model.enums.PaymentStatus
import com.ridehailing.model.enums.TripStatus
import com.ridehailing.model.payment.Payment
import com.ridehailing.model.dto.CreatePaymentRequest
import com.ridehailing.service.payment.PaymentGateway
import com.ridehailing.util.IdempotencyUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
  private val paymentMapper: PaymentMapper,
  private val tripMapper: TripMapper,
  private val tenantService: TenantService,
  private val paymentGateway: PaymentGateway
) {

  private val log = LoggerFactory.getLogger(PaymentService::class.java)

  @Transactional
  fun createPayment(request: CreatePaymentRequest): Payment {
    log.info("createPayment - Creating payment for trip: ${request.tripId}")

    // Validate payment method
    PaymentMethod.entries.firstOrNull { it.id == request.paymentMethodId }
      ?: throw ApplicationException(ApplicationExceptionTypes.INVALID_TYPE_ID, "Invalid paymentMethodId: ${request.paymentMethodId}")

    val trip = tripMapper.findById(request.tripId)
      ?: throw ApplicationException(ApplicationExceptionTypes.TRIP_NOT_FOUND)

    if (trip.status?.id != TripStatus.COMPLETED.id) {
      throw ApplicationException(ApplicationExceptionTypes.INVALID_TRIP_STATUS,
        "Trip must be completed before payment. Current: ${trip.status?.id}")
    }

    if (trip.totalFare == null) {
      throw ApplicationException(ApplicationExceptionTypes.TRIP_FARE_NOT_CALCULATED)
    }

    // Server-generated idempotency key
    val idempotencyKey = IdempotencyUtil.generateKey(request.tripId, request.paymentMethodId)

    // Check for duplicate
    val existing = paymentMapper.findByIdempotencyKey(idempotencyKey)
    if (existing != null) {
      log.info("createPayment - Duplicate payment detected, returning existing: ${existing.id}")
      return existing
    }

    val tenantId = tenantService.getDefaultTenantId()

    val payment = Payment(
      tenantId = tenantId,
      tripId = request.tripId,
      riderId = trip.riderId,
      amount = trip.totalFare,
      status = IdName(PaymentStatus.PENDING.id),
      paymentMethod = IdName(request.paymentMethodId),
      idempotencyKey = idempotencyKey
    )

    paymentMapper.insert(payment)
    log.info("createPayment - Payment record created, amount: ${payment.amount}")

    // Fetch the inserted payment
    val createdPayment = paymentMapper.findByIdempotencyKey(idempotencyKey)!!

    // Process via Razorpay
    processPayment(createdPayment)

    return paymentMapper.findById(createdPayment.id!!)!!
  }

  fun getPayment(paymentId: java.util.UUID): Payment {
    log.info("getPayment - Fetching payment: $paymentId")
    return paymentMapper.findById(paymentId)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_FOUND) // reuse for now
  }

  private fun processPayment(payment: Payment) {
    log.info("processPayment - Processing payment: ${payment.id} via PSP")

    paymentMapper.updateStatus(payment.id!!, PaymentStatus.PROCESSING.id, null)

    try {
      val orderResponse = paymentGateway.createOrder(
        amount = payment.amount,
        currency = payment.currency,
        receiptId = payment.id.toString()
      )

      // In a real flow, the frontend would complete the payment using the orderId
      // For now, we mark it as completed with the PSP reference
      paymentMapper.updateStatus(payment.id, PaymentStatus.COMPLETED.id, orderResponse.pspReference)
      log.info("processPayment - Payment ${payment.id} completed, PSP ref: ${orderResponse.pspReference}")

    } catch (e: Exception) {
      log.error("processPayment - Payment ${payment.id} failed: ${e.message}", e)
      paymentMapper.updateStatus(payment.id, PaymentStatus.FAILED.id, null)
    }
  }
}
