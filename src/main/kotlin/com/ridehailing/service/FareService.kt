package com.ridehailing.service

import com.ridehailing.config.Constant
import com.ridehailing.model.enums.VehicleType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class FareService {

  private val log = LoggerFactory.getLogger(FareService::class.java)

  data class FareBreakdown(
    val baseFare: BigDecimal,
    val distanceFare: BigDecimal,
    val timeFare: BigDecimal,
    val surgeMultiplier: BigDecimal,
    val totalFare: BigDecimal
  )

  fun calculateFare(
    distanceKm: BigDecimal,
    durationMinutes: BigDecimal,
    vehicleType: VehicleType,
    surgeMultiplier: BigDecimal = BigDecimal.ONE
  ): FareBreakdown {
    log.info("calculateFare - distanceKm=$distanceKm, durationMin=$durationMinutes, type=$vehicleType, surge=$surgeMultiplier")

    val (base, perKm, perMin) = when (vehicleType) {
      VehicleType.ECONOMY -> Triple(BigDecimal(Constant.Fare.ECONOMY_BASE), BigDecimal(Constant.Fare.ECONOMY_PER_KM), BigDecimal(Constant.Fare.ECONOMY_PER_MIN))
      VehicleType.PREMIUM -> Triple(BigDecimal(Constant.Fare.PREMIUM_BASE), BigDecimal(Constant.Fare.PREMIUM_PER_KM), BigDecimal(Constant.Fare.PREMIUM_PER_MIN))
      VehicleType.SUV -> Triple(BigDecimal(Constant.Fare.SUV_BASE), BigDecimal(Constant.Fare.SUV_PER_KM), BigDecimal(Constant.Fare.SUV_PER_MIN))
    }

    val distanceFare = perKm.multiply(distanceKm).setScale(2, RoundingMode.HALF_UP)
    val timeFare = perMin.multiply(durationMinutes).setScale(2, RoundingMode.HALF_UP)
    val subtotal = base.add(distanceFare).add(timeFare)
    val total = subtotal.multiply(surgeMultiplier).setScale(2, RoundingMode.HALF_UP)

    log.info("calculateFare - base=$base, distance=$distanceFare, time=$timeFare, total=$total")

    return FareBreakdown(base, distanceFare, timeFare, surgeMultiplier, total)
  }

  fun estimateFare(
    pickupLat: Double, pickupLng: Double,
    destLat: Double, destLng: Double,
    vehicleType: VehicleType,
    surgeMultiplier: BigDecimal = BigDecimal.ONE
  ): BigDecimal {
    log.info("estimateFare - from ($pickupLat,$pickupLng) to ($destLat,$destLng)")
    val distanceKm = haversineDistance(pickupLat, pickupLng, destLat, destLng)
    val durationMinutes = distanceKm
      .divide(BigDecimal(Constant.Fare.AVG_CITY_SPEED_KMH), 2, RoundingMode.HALF_UP)
      .multiply(BigDecimal("60")).setScale(2, RoundingMode.HALF_UP)
    return calculateFare(distanceKm, durationMinutes, vehicleType, surgeMultiplier).totalFare
  }

  fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): BigDecimal {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
      Math.sin(dLng / 2) * Math.sin(dLng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return BigDecimal(r * c).setScale(3, RoundingMode.HALF_UP)
  }
}
