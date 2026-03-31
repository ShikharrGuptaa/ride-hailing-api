package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.config.Constant
import com.ridehailing.mapper.DriverMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.driver.Driver
import com.ridehailing.model.driver.DriverCurrentLocation
import com.ridehailing.model.driver.DriverLocation
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.Region
import com.ridehailing.model.enums.VehicleType
import com.ridehailing.model.dto.CreateDriverRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class DriverService(
  private val driverMapper: DriverMapper,
  private val tenantService: TenantService,
  private val redisTemplate: RedisTemplate<String, Any>
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  fun createDriver(request: CreateDriverRequest): Driver {
    log.info("createDriver - Creating driver: ${request.name}, phone: ${request.phone}")

    VehicleType.entries.firstOrNull { it.id == request.vehicleTypeId }
      ?: throw ApplicationException(ApplicationExceptionTypes.INVALID_TYPE_ID, "Invalid vehicleTypeId: ${request.vehicleTypeId}")

    if (request.regionId != null) {
      Region.entries.firstOrNull { it.id == request.regionId }
        ?: throw ApplicationException(ApplicationExceptionTypes.INVALID_TYPE_ID, "Invalid regionId: ${request.regionId}")
    }

    val tenantId = tenantService.getDefaultTenantId()

    val existing = driverMapper.findByPhoneAndTenant(request.phone, tenantId)
    if (existing != null) {
      log.info("createDriver - Driver already exists with phone: ${request.phone}")
      return existing
    }

    val driver = Driver(
      tenantId = tenantId,
      region = request.regionId?.let { IdName(it) },
      name = request.name,
      phone = request.phone,
      vehicleType = IdName(request.vehicleTypeId),
      licensePlate = request.licensePlate,
      status = IdName(DriverStatus.OFFLINE.id)
    )

    driverMapper.insert(driver)
    log.info("createDriver - Driver created")
    return driverMapper.findByPhoneAndTenant(driver.phone!!, tenantId)!!
  }

  fun findById(driverId: UUID): Driver {
    log.debug("findById - Looking up driver: $driverId")
    return driverMapper.findById(driverId)
      ?: throw ApplicationException(ApplicationExceptionTypes.DRIVER_NOT_FOUND)
  }

  fun findByPhone(phone: String): Driver? {
    log.debug("findByPhone - Looking up driver by phone: $phone")
    val tenantId = tenantService.getDefaultTenantId()
    return driverMapper.findByPhoneAndTenant(phone, tenantId)
  }

  fun updateLocation(driverId: UUID, lat: Double, lng: Double): DriverCurrentLocation {
    log.info("updateLocation - Updating location for driver: $driverId to ($lat, $lng)")

    findById(driverId)

    val currentLocation = DriverCurrentLocation(driverId = driverId, lat = lat, lng = lng)
    driverMapper.upsertCurrentLocation(currentLocation)

    val locationKey = "${Constant.Redis.DRIVER_LOCATION_KEY}$driverId"
    val locationData = mapOf("lat" to lat, "lng" to lng, "timestamp" to System.currentTimeMillis())
    redisTemplate.opsForValue().set(locationKey, locationData, Constant.Redis.LOCATION_TTL_SECONDS, TimeUnit.SECONDS)

    driverMapper.insertLocation(DriverLocation(driverId = driverId, lat = lat, lng = lng))
    redisTemplate.delete("${Constant.Redis.DRIVER_CACHE_KEY}$driverId")

    log.debug("updateLocation - Location updated successfully for driver: $driverId")
    return currentLocation
  }

  fun findNearbyAvailable(lat: Double, lng: Double, vehicleTypeId: Int, radiusKm: Double = Constant.DriverMatching.DEFAULT_SEARCH_RADIUS_KM): List<Driver> {
    log.info("findNearbyAvailable - Searching for vehicleTypeId=$vehicleTypeId near ($lat, $lng) within ${radiusKm}km")
    val drivers = driverMapper.findNearbyAvailable(lat, lng, vehicleTypeId, radiusKm, Constant.DriverMatching.MAX_NEARBY_DRIVERS)
    log.info("findNearbyAvailable - Found ${drivers.size} available drivers")
    return drivers
  }

  fun findCurrentLocation(driverId: UUID): DriverCurrentLocation? {
    log.debug("findCurrentLocation - Looking up current location for driver: $driverId")
    return driverMapper.findCurrentLocation(driverId)
  }

  fun updateStatus(driverId: UUID, statusId: Int): Int {
    log.info("updateStatus - Setting driver $driverId statusId to $statusId")
    redisTemplate.delete("${Constant.Redis.DRIVER_CACHE_KEY}$driverId")
    return driverMapper.updateStatus(driverId, statusId)
  }

  fun updateVehicleType(driverId: UUID, vehicleTypeId: Int, licensePlate: String?): Driver {
    log.info("updateVehicleType - Driver $driverId switching to vehicleTypeId=$vehicleTypeId")
    VehicleType.entries.firstOrNull { it.id == vehicleTypeId }
      ?: throw ApplicationException(ApplicationExceptionTypes.INVALID_TYPE_ID, "Invalid vehicleTypeId: $vehicleTypeId")
    driverMapper.updateVehicleType(driverId, vehicleTypeId, licensePlate)
    redisTemplate.delete("${Constant.Redis.DRIVER_CACHE_KEY}$driverId")
    return findById(driverId)
  }
}
