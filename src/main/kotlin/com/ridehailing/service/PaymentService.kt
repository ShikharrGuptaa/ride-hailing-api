package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.mapper.PaymentMapper
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.PaymentMethod
import com.ridehailing.model.enums.PaymentStatus
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.TripStatus
import com.ridehailing.model.payment.Payment
import com.ridehailing.model.dto.CreatePaymentRequest
import com.ridehailing.service.payment.PaymentGateway
import com.ridehailing.util.IdempotencyUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PaymentService(
  private val paymentMapper: PaymentMapper,
  private val tripMapper: TripMapper,
  private val rideMapper: RideMapper,
  private val tenantService: TenantService,
  private val paymentGateway: PaymentGateway,
  private val rideEventService: RideEventService
) {

  private val log = LoggerFactory.getLogger(PaymentService::class.java)

  @Transactional
  fun createPayment(request: CreatePaymentRequest): Payment {
    log.info("createPayment - Creating payment for trip: ${request.tripId}")

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

    val idempotencyKey = IdempotencyUtil.generateKey(request.tripId, request.paymentMethodId)

    val existing = paymentMapper.findByIdempotencyKey(idempotencyKey)
    if (existing != null) {
      log.info("createPayment - Duplicate payment detected, returning existing: ${existing.id}")
      return existing
    }

    val tenantId = tenantService.getDefaultTenantId()

    // Create Razorpay order first
    val orderResponse = paymentGateway.createOrder(
      amount = trip.totalFare,
      currency = "INR",
      receiptId = idempotencyKey.take(40)
    )

    val payment = Payment(
      tenantId = tenantId,
      tripId = request.tripId,
      riderId = trip.riderId,
      amount = trip.totalFare,
      status = IdName(PaymentStatus.PENDING.id),
      paymentMethod = IdName(request.paymentMethodId),
      pspReference = orderResponse.pspReference,
      idempotencyKey = idempotencyKey
    )

    paymentMapper.insert(payment)
    log.info("createPayment - Payment created with Razorpay order: ${orderResponse.pspReference}, amount: ${payment.amount}")

    return paymentMapper.findByIdempotencyKey(idempotencyKey)!!
  }

  @Transactional
  fun confirmPayment(paymentId: UUID, razorpayPaymentId: String): Payment {
    log.info("confirmPayment - Confirming payment: $paymentId, razorpayPaymentId: $razorpayPaymentId")

    val payment = paymentMapper.findById(paymentId)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_FOUND)

    if (payment.status?.id != PaymentStatus.PENDING.id) {
      throw ApplicationException(ApplicationExceptionTypes.INVALID_TRIP_STATUS,
        "Payment is not in PENDING status")
    }

    paymentMapper.updateStatus(paymentId, PaymentStatus.COMPLETED.id, razorpayPaymentId)

    // Mark ride as COMPLETED now that payment is confirmed
    val trip = tripMapper.findById(payment.tripId!!)
    if (trip?.rideId != null) {
      rideMapper.updateStatus(trip.rideId, RideStatus.COMPLETED.id, RideStatus.PAYMENT_PENDING.id)
      val completedRide = rideMapper.findById(trip.rideId)!!
      rideEventService.broadcastRideUpdate(completedRide)
      // Notify driver that payment was confirmed so earnings refresh instantly
      if (trip.driverId != null) {
        rideEventService.broadcastPaymentConfirmed(trip.driverId, mapOf(
          "tripId" to trip.id.toString(),
          "amount" to (trip.totalFare?.toString() ?: "0"),
          "status" to "COMPLETED"
        ))
      }
      log.info("confirmPayment - Ride ${trip.rideId} marked COMPLETED after payment")
    }

    log.info("confirmPayment - Payment $paymentId confirmed with razorpay payment: $razorpayPaymentId")
    return paymentMapper.findById(paymentId)!!
  }

  fun getPaymentByTrip(tripId: UUID): Payment? {
    log.info("getPaymentByTrip - Fetching payment for trip: $tripId")
    return paymentMapper.findByTripId(tripId)
  }

  fun getPayment(paymentId: UUID): Payment {
    log.info("getPayment - Fetching payment: $paymentId")
    return paymentMapper.findById(paymentId)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_FOUND)
  }
}
