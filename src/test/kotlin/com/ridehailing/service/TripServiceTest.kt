package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.TripStatus
import com.ridehailing.model.enums.VehicleType
import com.ridehailing.model.ride.Ride
import com.ridehailing.model.trip.Trip
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TripServiceTest {

  @Mock lateinit var tripMapper: TripMapper
  @Mock lateinit var rideMapper: RideMapper
  @Mock lateinit var fareService: FareService
  @Mock lateinit var driverService: DriverService
  @Mock lateinit var redisTemplate: RedisTemplate<String, Any>
  @Mock lateinit var rideEventService: RideEventService

  @InjectMocks lateinit var tripService: TripService

  private val tenantId = UUID.randomUUID()
  private val driverId = UUID.randomUUID()
  private val riderId = UUID.randomUUID()

  @Test
  fun `getTrip - not found throws exception`() {
    val tripId = UUID.randomUUID()
    whenever(tripMapper.findById(tripId)).thenReturn(null)

    val ex = assertThrows<ApplicationException> { tripService.getTrip(tripId) }
    assertEquals(ApplicationExceptionTypes.TRIP_NOT_FOUND.first, ex.code)
  }

  @Test
  fun `endTrip - trip not in progress throws exception`() {
    val tripId = UUID.randomUUID()
    val trip = Trip(
      id = tripId, tenantId = tenantId, rideId = UUID.randomUUID(),
      driverId = driverId, riderId = riderId,
      status = IdName(TripStatus.COMPLETED.id),
      startLat = 19.0, startLng = 72.0
    )
    whenever(tripMapper.findById(tripId)).thenReturn(trip)

    val ex = assertThrows<ApplicationException> { tripService.endTrip(tripId, null, null) }
    assertEquals(ApplicationExceptionTypes.INVALID_TRIP_STATUS.first, ex.code)
  }

  @Test
  fun `endTrip - success calculates fare and completes`() {
    val tripId = UUID.randomUUID()
    val rideId = UUID.randomUUID()

    val trip = Trip(
      id = tripId, tenantId = tenantId, rideId = rideId,
      driverId = driverId, riderId = riderId,
      status = IdName(TripStatus.IN_PROGRESS.id),
      startLat = 19.076, startLng = 72.877,
      startedAt = OffsetDateTime.now().minusMinutes(20)
    )

    val ride = Ride(
      id = rideId, tenantId = tenantId, riderId = riderId,
      status = IdName(RideStatus.DRIVER_ACCEPTED.id),
      pickupLat = 19.076, pickupLng = 72.877,
      destinationLat = 19.100, destinationLng = 72.900,
      vehicleType = IdName(VehicleType.ECONOMY.id)
    )

    val fareBreakdown = FareService.FareBreakdown(
      baseFare = BigDecimal("30"), distanceFare = BigDecimal("36.00"),
      timeFare = BigDecimal("30.00"), surgeMultiplier = BigDecimal.ONE,
      totalFare = BigDecimal("96.00")
    )

    whenever(tripMapper.findById(tripId)).thenReturn(trip)
    whenever(rideMapper.findById(rideId)).thenReturn(ride)
    whenever(fareService.haversineDistance(any(), any(), any(), any())).thenReturn(BigDecimal("3.000"))
    whenever(fareService.calculateFare(any(), any(), any(), any())).thenReturn(fareBreakdown)
    whenever(tripMapper.endTrip(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1)
    whenever(rideMapper.updateStatus(any(), any(), any())).thenReturn(1)
    whenever(driverService.updateStatus(any(), any())).thenReturn(1)
    whenever(redisTemplate.delete(any<String>())).thenReturn(true)

    val completedTrip = trip.copy(
      status = IdName(TripStatus.COMPLETED.id),
      totalFare = BigDecimal("96.00")
    )
    whenever(tripMapper.findById(tripId)).thenReturn(trip, completedTrip)
    whenever(rideMapper.findById(rideId)).thenReturn(ride, ride)

    val result = tripService.endTrip(tripId, 19.100, 72.900)
    assertEquals(TripStatus.COMPLETED.id, result.status?.id)
    assertEquals(BigDecimal("96.00"), result.totalFare)
    verify(driverService).updateStatus(driverId, com.ridehailing.model.enums.DriverStatus.ONLINE.id)
  }
}
