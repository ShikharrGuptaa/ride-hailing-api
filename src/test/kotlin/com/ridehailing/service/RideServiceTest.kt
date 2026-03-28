package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.driver.Driver
import com.ridehailing.model.dto.CreateRideRequest
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.VehicleType
import com.ridehailing.model.ride.Ride
import com.ridehailing.model.rider.Rider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.math.BigDecimal
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RideServiceTest {

  @Mock lateinit var rideMapper: RideMapper
  @Mock lateinit var tripMapper: TripMapper
  @Mock lateinit var fareService: FareService
  @Mock lateinit var driverService: DriverService
  @Mock lateinit var riderService: RiderService
  @Mock lateinit var tenantService: TenantService
  @Mock lateinit var redisTemplate: RedisTemplate<String, Any>
  @Mock lateinit var valueOps: ValueOperations<String, Any>

  @InjectMocks lateinit var rideService: RideService

  private val tenantId = UUID.randomUUID()
  private val riderId = UUID.randomUUID()
  private val driverId = UUID.randomUUID()

  @Test
  fun `createRide - success with driver matching`() {
    val request = CreateRideRequest(
      riderId = riderId, pickupLat = 19.076, pickupLng = 72.877,
      destinationLat = 19.100, destinationLng = 72.900,
      vehicleTypeId = VehicleType.ECONOMY.id
    )

    val rider = Rider(id = riderId, tenantId = tenantId, name = "Test", phone = "9876543210")
    whenever(riderService.findById(riderId)).thenReturn(rider)
    whenever(tenantService.getDefaultTenantId()).thenReturn(tenantId)
    whenever(fareService.estimateFare(any(), any(), any(), any(), any(), any())).thenReturn(BigDecimal("150.00"))

    val createdRide = Ride(
      id = UUID.randomUUID(), tenantId = tenantId, riderId = riderId,
      status = IdName(RideStatus.REQUESTED.id), pickupLat = 19.076, pickupLng = 72.877,
      destinationLat = 19.100, destinationLng = 72.900,
      vehicleType = IdName(VehicleType.ECONOMY.id), estimatedFare = BigDecimal("150.00")
    )
    whenever(rideMapper.findByIdempotencyKey(any())).thenReturn(null, createdRide)
    whenever(driverService.findNearbyAvailable(any(), any(), any(), any())).thenReturn(emptyList())
    whenever(rideMapper.findById(any())).thenReturn(createdRide)

    val result = rideService.createRide(request)
    assertNotNull(result)
    assertEquals(BigDecimal("150.00"), result.estimatedFare)
    verify(rideMapper).insert(any())
  }

  @Test
  fun `createRide - idempotency returns existing ride`() {
    val request = CreateRideRequest(
      riderId = riderId, pickupLat = 19.076, pickupLng = 72.877,
      destinationLat = 19.100, destinationLng = 72.900,
      vehicleTypeId = VehicleType.ECONOMY.id
    )

    val rider = Rider(id = riderId, tenantId = tenantId, name = "Test", phone = "9876543210")
    whenever(riderService.findById(riderId)).thenReturn(rider)

    val existingRide = Ride(
      id = UUID.randomUUID(), tenantId = tenantId, riderId = riderId,
      status = IdName(RideStatus.REQUESTED.id), pickupLat = 19.076, pickupLng = 72.877,
      destinationLat = 19.100, destinationLng = 72.900,
      vehicleType = IdName(VehicleType.ECONOMY.id)
    )
    whenever(rideMapper.findByIdempotencyKey(any())).thenReturn(existingRide)

    val result = rideService.createRide(request)
    assertEquals(existingRide.id, result.id)
    verify(rideMapper, never()).insert(any())
  }

  @Test
  fun `createRide - invalid vehicleTypeId throws exception`() {
    val request = CreateRideRequest(
      riderId = riderId, pickupLat = 19.076, pickupLng = 72.877,
      destinationLat = 19.100, destinationLng = 72.900,
      vehicleTypeId = 999
    )

    val rider = Rider(id = riderId, tenantId = tenantId, name = "Test", phone = "9876543210")
    whenever(riderService.findById(riderId)).thenReturn(rider)

    val ex = assertThrows<ApplicationException> { rideService.createRide(request) }
    assertEquals(ApplicationExceptionTypes.INVALID_TYPE_ID.first, ex.code)
  }

  @Test
  fun `getRide - not found throws exception`() {
    val rideId = UUID.randomUUID()
    whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
    whenever(valueOps.get(any())).thenReturn(null)
    whenever(rideMapper.findById(rideId)).thenReturn(null)

    val ex = assertThrows<ApplicationException> { rideService.getRide(rideId) }
    assertEquals(ApplicationExceptionTypes.RIDE_NOT_FOUND.first, ex.code)
  }

  @Test
  fun `acceptRide - wrong status throws exception`() {
    val rideId = UUID.randomUUID()
    val ride = Ride(
      id = rideId, tenantId = tenantId, riderId = riderId,
      status = IdName(RideStatus.REQUESTED.id), pickupLat = 19.0, pickupLng = 72.0,
      destinationLat = 19.1, destinationLng = 72.1,
      vehicleType = IdName(VehicleType.ECONOMY.id)
    )
    whenever(rideMapper.findById(rideId)).thenReturn(ride)

    val ex = assertThrows<ApplicationException> { rideService.acceptRide(driverId, rideId) }
    assertEquals(ApplicationExceptionTypes.INVALID_RIDE_STATUS.first, ex.code)
  }

  @Test
  fun `acceptRide - wrong driver throws exception`() {
    val rideId = UUID.randomUUID()
    val otherDriverId = UUID.randomUUID()
    val ride = Ride(
      id = rideId, tenantId = tenantId, riderId = riderId, driverId = otherDriverId,
      status = IdName(RideStatus.DRIVER_ASSIGNED.id), pickupLat = 19.0, pickupLng = 72.0,
      destinationLat = 19.1, destinationLng = 72.1,
      vehicleType = IdName(VehicleType.ECONOMY.id)
    )
    whenever(rideMapper.findById(rideId)).thenReturn(ride)

    val ex = assertThrows<ApplicationException> { rideService.acceptRide(driverId, rideId) }
    assertEquals(ApplicationExceptionTypes.RIDE_NOT_ASSIGNED_TO_DRIVER.first, ex.code)
  }
}
