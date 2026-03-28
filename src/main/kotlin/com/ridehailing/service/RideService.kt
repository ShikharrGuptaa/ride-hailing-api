package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.config.Constant
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.driver.Driver
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
  private val redisTemplate: RedisTemplate<String, Any>
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

    // Attempt auto-matching
    matchDriver(createdRide)

    return rideMapper.findById(createdRide.id!!)!!
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

    if (ride.status?.id != RideStatus.DRIVER_ASSIGNED.id) {
      throw ApplicationException(ApplicationExceptionTypes.INVALID_RIDE_STATUS,
        "Expected DRIVER_ASSIGNED(${RideStatus.DRIVER_ASSIGNED.id}), got ${ride.status?.id}")
    }

    if (ride.driverId != driverId) {
      throw ApplicationException(ApplicationExceptionTypes.RIDE_NOT_ASSIGNED_TO_DRIVER)
    }

    // Check driver doesn't have another active ride
    val activeRide = rideMapper.findActiveByDriverId(driverId)
    if (activeRide != null && activeRide.id != rideId) {
      throw ApplicationException(ApplicationExceptionTypes.DRIVER_HAS_ACTIVE_RIDE)
    }

    val updated = rideMapper.updateStatus(rideId, RideStatus.DRIVER_ACCEPTED.id, RideStatus.DRIVER_ASSIGNED.id)
    if (updated == 0) {
      throw ApplicationException(ApplicationExceptionTypes.CONCURRENT_MODIFICATION)
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
    return rideMapper.findById(rideId)!!
  }

  fun getActiveRideForDriver(driverId: UUID): Ride? {
    log.debug("getActiveRideForDriver - Checking for active ride for driver: $driverId")
    return rideMapper.findActiveByDriverId(driverId)
  }

  private fun matchDriver(ride: Ride) {
    log.info("matchDriver - Attempting to match driver for ride: ${ride.id}")

    val nearbyDrivers = driverService.findNearbyAvailable(
      ride.pickupLat, ride.pickupLng, ride.vehicleType?.id!!
    )

    if (nearbyDrivers.isEmpty()) {
      log.warn("matchDriver - No available drivers found for ride: ${ride.id}")
      return
    }

    val bestDriver = nearbyDrivers.first()
    val assigned = rideMapper.assignDriver(ride.id!!, bestDriver.id!!, RideStatus.DRIVER_ASSIGNED.id)

    if (assigned > 0) {
      log.info("matchDriver - Assigned driver ${bestDriver.id} to ride ${ride.id}")
    } else {
      log.warn("matchDriver - Failed to assign driver to ride ${ride.id}")
    }
  }
}
