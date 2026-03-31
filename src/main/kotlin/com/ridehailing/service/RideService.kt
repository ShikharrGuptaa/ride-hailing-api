package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.config.Constant
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.Region
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.VehicleType
import com.ridehailing.model.ride.Ride
import com.ridehailing.model.trip.Trip
import com.ridehailing.model.dto.CreateRideRequest
import com.ridehailing.util.IdempotencyUtil
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class RideService(
  private val rideMapper: RideMapper,
  private val tripMapper: TripMapper,
  private val fareService: FareService,
  private val driverService: DriverService,
  private val riderService: RiderService,
  private val tenantService: TenantService,
  private val redisTemplate: RedisTemplate<String, Any>,
  private val rideEventService: RideEventService
) {

  private val log = LoggerFactory.getLogger(RideService::class.java)

  @Transactional
  fun createRide(request: CreateRideRequest): Ride {
    log.info("createRide - Creating ride for rider: ${request.riderId}")

    // Validate rider exists
    riderService.findById(request.riderId)

    // Validate vehicleTypeId
    val vehicleType = VehicleType.entries.firstOrNull { it.id == request.vehicleTypeId }
      ?: throw ApplicationException(ApplicationExceptionTypes.INVALID_TYPE_ID, "Invalid vehicleTypeId: ${request.vehicleTypeId}")

    // Validate regionId
    if (request.regionId != null) {
      Region.entries.firstOrNull { it.id == request.regionId }
        ?: throw ApplicationException(ApplicationExceptionTypes.INVALID_TYPE_ID, "Invalid regionId: ${request.regionId}")
    }

    // Server-generated idempotency key
    val idempotencyKey = IdempotencyUtil.generateKey(
      request.riderId, request.pickupLat, request.pickupLng,
      request.destinationLat, request.destinationLng, request.vehicleTypeId
    )

    // Check for duplicate
    val existing = rideMapper.findByIdempotencyKey(idempotencyKey)
    if (existing != null) {
      log.info("createRide - Duplicate ride detected, returning existing: ${existing.id}")
      return existing
    }

    val tenantId = tenantService.getDefaultTenantId()

    // Estimate fare
    val estimatedFare = fareService.estimateFare(
      request.pickupLat, request.pickupLng,
      request.destinationLat, request.destinationLng,
      vehicleType
    )

    val ride = Ride(
      tenantId = tenantId,
      region = request.regionId?.let { IdName(it) },
      riderId = request.riderId,
      status = IdName(RideStatus.REQUESTED.id),
      pickupLat = request.pickupLat,
      pickupLng = request.pickupLng,
      pickupAddress = request.pickupAddress,
      destinationLat = request.destinationLat,
      destinationLng = request.destinationLng,
      destinationAddress = request.destinationAddress,
      vehicleType = IdName(request.vehicleTypeId),
      estimatedFare = estimatedFare,
      idempotencyKey = idempotencyKey
    )

    rideMapper.insert(ride)
    log.info("createRide - Ride created, estimated fare: $estimatedFare")

    // Fetch the inserted ride
    val createdRide = rideMapper.findByIdempotencyKey(idempotencyKey)!!

    // Broadcast new ride to drivers
    rideEventService.broadcastNewRide(createdRide)

    return createdRide
  }

  fun getRide(rideId: UUID): Ride {
    log.info("getRide - Fetching ride: $rideId")

    val cacheKey = "${Constant.Redis.RIDE_CACHE_KEY}$rideId"
    val cached = redisTemplate.opsForValue().get(cacheKey)
    if (cached != null) {
      log.debug("getRide - Cache hit for ride: $rideId")
    }

    val ride = rideMapper.findById(rideId)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_FOUND)

    // Cache non-terminal rides
    if (ride.status?.id !in listOf(RideStatus.COMPLETED.id, RideStatus.CANCELLED.id)) {
      redisTemplate.opsForValue().set(cacheKey, rideId.toString(), Constant.Redis.RIDE_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
    }

    return ride
  }

  @Transactional
  fun acceptRide(driverId: UUID, rideId: UUID): Ride {
    log.info("acceptRide - Driver $driverId accepting ride $rideId")

    val ride = rideMapper.findById(rideId)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_FOUND)

    if (ride.status?.id != RideStatus.REQUESTED.id) {
      throw ApplicationException(ApplicationExceptionTypes.INVALID_RIDE_STATUS,
        "Expected REQUESTED(${RideStatus.REQUESTED.id}), got ${ride.status?.id}")
    }

    // Validate driver proximity to pickup
    val driver = driverService.findById(driverId)
    val driverLocation = driverService.findCurrentLocation(driverId)
    if (driverLocation != null) {
      val distanceKm = haversineDistance(driverLocation.lat, driverLocation.lng, ride.pickupLat, ride.pickupLng)
      if (distanceKm > Constant.DriverMatching.ACCEPT_RADIUS_KM) {
        log.warn("acceptRide - Driver $driverId is ${distanceKm}km from pickup, exceeds ${Constant.DriverMatching.ACCEPT_RADIUS_KM}km limit")
        throw ApplicationException(ApplicationExceptionTypes.DRIVER_TOO_FAR,
          "Driver is ${String.format("%.1f", distanceKm)}km from pickup. Maximum allowed: ${Constant.DriverMatching.ACCEPT_RADIUS_KM}km")
      }
    }

    // Validate region match if ride has a region
    if (ride.region?.id != null && driver.region?.id != null && ride.region.id != driver.region.id) {
      log.warn("acceptRide - Driver region ${driver.region.id} does not match ride region ${ride.region.id}")
      throw ApplicationException(ApplicationExceptionTypes.REGION_MISMATCH,
        "Driver region does not match ride region")
    }

    // First driver to accept wins (optimistic lock via expectedStatus)
    val updated = rideMapper.assignDriver(rideId, driverId, RideStatus.DRIVER_ACCEPTED.id)
    if (updated == 0) {
      throw ApplicationException(ApplicationExceptionTypes.CONCURRENT_MODIFICATION,
        "Ride already taken by another driver")
    }

    // Create trip
    val trip = Trip(
      tenantId = ride.tenantId,
      region = ride.region,
      rideId = rideId,
      driverId = driverId,
      riderId = ride.riderId,
      startLat = ride.pickupLat,
      startLng = ride.pickupLng,
      surgeMultiplier = ride.surgeMultiplier
    )
    tripMapper.insert(trip)

    // Driver goes ON_TRIP
    driverService.updateStatus(driverId, DriverStatus.ON_TRIP.id)

    // Invalidate cache
    redisTemplate.delete("${Constant.Redis.RIDE_CACHE_KEY}$rideId")

    log.info("acceptRide - Ride $rideId accepted by driver $driverId")
    val acceptedRide = rideMapper.findById(rideId)!!

    // Broadcast ride update to rider
    rideEventService.broadcastRideUpdate(acceptedRide)

    return acceptedRide
  }

  fun findAvailableRides(vehicleTypeId: Int, driverLat: Double, driverLng: Double, regionId: Int?): List<Ride> {
    log.info("findAvailableRides - Finding REQUESTED rides for vehicleTypeId: $vehicleTypeId within ${Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM}km of ($driverLat, $driverLng), regionId: $regionId")
    return rideMapper.findAvailableByVehicleType(vehicleTypeId, driverLat, driverLng, Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM, regionId)
  }

  fun getActiveRideForDriver(driverId: UUID): Ride? {
    log.debug("getActiveRideForDriver - Checking for active ride for driver: $driverId")
    return rideMapper.findActiveByDriverId(driverId)
  }

  fun getFareService(): FareService = fareService

  @Transactional
  fun cancelRide(rideId: UUID, riderId: UUID): Ride {
    log.info("cancelRide - Rider $riderId cancelling ride $rideId")

    val ride = rideMapper.findById(rideId)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_FOUND)

    if (ride.riderId != riderId) {
      throw ApplicationException(ApplicationExceptionTypes.UNAUTHORIZED, "Rider does not own this ride")
    }

    // Can only cancel before trip is in progress
    if (ride.status?.id!! >= RideStatus.IN_PROGRESS.id) {
      throw ApplicationException(ApplicationExceptionTypes.INVALID_RIDE_STATUS,
        "Cannot cancel ride in status ${ride.status.id}")
    }

    val updated = rideMapper.updateStatus(rideId, RideStatus.CANCELLED.id, ride.status.id)
    if (updated == 0) {
      throw ApplicationException(ApplicationExceptionTypes.CONCURRENT_MODIFICATION, "Ride status changed concurrently")
    }

    // Free up driver if one was assigned
    if (ride.driverId != null) {
      driverService.updateStatus(ride.driverId, DriverStatus.ONLINE.id)
    }

    redisTemplate.delete("${Constant.Redis.RIDE_CACHE_KEY}$rideId")

    val cancelledRide = rideMapper.findById(rideId)!!
    rideEventService.broadcastRideUpdate(cancelledRide)
    log.info("cancelRide - Ride $rideId cancelled successfully")
    return cancelledRide
  }

  fun getDriverEarnings(driverId: UUID): Map<String, Any>? {
    log.info("getDriverEarnings - Fetching earnings for driver: $driverId")
    return tripMapper.getDriverEarnings(driverId)
  }

  private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
      Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  }
}
