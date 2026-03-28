package com.ridehailing.mapper

import com.ridehailing.model.payment.Payment
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.util.UUID

@Mapper
interface PaymentMapper {

  fun insert(payment: Payment)

  fun findById(@Param("id") id: UUID): Payment?

  fun findByIdempotencyKey(@Param("idempotencyKey") idempotencyKey: String): Payment?

  fun updateStatus(
    @Param("id") id: UUID,
    @Param("statusId") statusId: Int,
    @Param("pspReference") pspReference: String?
  ): Int
  fun findByTripId(@Param("tripId") tripId: UUID): Payment?
}
