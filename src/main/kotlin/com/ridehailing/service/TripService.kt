package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.config.Constant
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.TripStatus
import com.ridehailing.model.enums.VehicleType
import com.ridehailing.model.trip.Trip
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@Service
class TripService(
  private val tripMapper: TripMapper,
  private val rideMapper: RideMapper,
  private val fareService: FareService,
  private val driverService: DriverService,
  private val redisTemplate: RedisTemplate<String, Any>,
  private val rideEventService: RideEventService
) {

  private val log = LoggerFactory.getLogger(TripService::class.java)

  fun getTrip(tripId: UUID): Trip {
    log.info("getTrip - Fetching trip: $tripId")
    return tripMapper.findById(tripId)
      ?: throw ApplicationException(ApplicationExceptionTypes.TRIP_NOT_FOUND)
  }

  fun getTripByRideId(rideId: UUID): Trip {
    log.info("getTripByRideId - Fetching trip for ride: $rideId")
    return tripMapper.findByRideId(rideId)
      ?: throw ApplicationException(ApplicationExceptionTypes.TRIP_NOT_FOUND)
  }

  @Transactional
  fun endTrip(tripId: UUID, endLat: Double?, endLng: Double?): Trip {
    log.info("endTrip - Ending trip: $tripId")

    val trip = tripMapper.findById(tripId)
      ?: throw ApplicationException(ApplicationExceptionTypes.TRIP_NOT_FOUND)

    if (trip.status?.id != TripStatus.IN_PROGRESS.id) {
      throw ApplicationException(ApplicationExceptionTypes.INVALID_TRIP_STATUS,
        "Expected IN_PROGRESS(${TripStatus.IN_PROGRESS.id}), got ${trip.status?.id}")
    }

    val ride = rideMapper.findById(trip.rideId!!)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_FOUND)

    val finalEndLat = endLat ?: ride.destinationLat
    val finalEndLng = endLng ?: ride.destinationLng

    // Calculate distance
    val distanceKm = fareService.haversineDistance(
      trip.startLat, trip.startLng, finalEndLat, finalEndLng
    )

    // Calculate duration based on distance (avg city speed 25 km/h) — same as estimate
    val durationMinutes = distanceKm
      .divide(BigDecimal(25), 2, RoundingMode.HALF_UP)
      .multiply(BigDecimal(60)).setScale(2, RoundingMode.HALF_UP)
      .let { if (it < BigDecimal.ONE) BigDecimal.ONE.setScale(2) else it }

    // Resolve vehicle type and calculate fare
    val vehicleType = VehicleType.entries.first { it.id == ride.vehicleType?.id }
    val fareBreakdown = fareService.calculateFare(
      distanceKm, durationMinutes, vehicleType, trip.surgeMultiplier
    )

    val updated = tripMapper.endTrip(
      id = tripId,
      endLat = finalEndLat,
      endLng = finalEndLng,
      distanceKm = distanceKm,
      durationMinutes = durationMinutes,
      baseFare = fareBreakdown.baseFare,
      distanceFare = fareBreakdown.distanceFare,
      timeFare = fareBreakdown.timeFare,
      totalFare = fareBreakdown.totalFare
    )

    if (updated == 0) {
      throw ApplicationException(ApplicationExceptionTypes.CONCURRENT_MODIFICATION)
    }

    // Ride moves to PAYMENT_PENDING — will be COMPLETED after payment confirmation
    rideMapper.updateStatus(ride.id!!, RideStatus.PAYMENT_PENDING.id, RideStatus.DRIVER_ACCEPTED.id)

    // Free up the driver
    driverService.updateStatus(trip.driverId!!, DriverStatus.ONLINE.id)

    // Invalidate cache
    redisTemplate.delete("${Constant.Redis.RIDE_CACHE_KEY}${ride.id}")

    log.info("endTrip - Trip $tripId ended. Distance: $distanceKm km, Fare: ${fareBreakdown.totalFare}")
    val completedRide = rideMapper.findById(ride.id!!)!!
    rideEventService.broadcastRideUpdate(completedRide)

    return tripMapper.findById(tripId)!!
  }
}
