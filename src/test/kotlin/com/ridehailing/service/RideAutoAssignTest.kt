package com.ridehailing.service

import com.ridehailing.config.Constant
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.IdName
import com.ridehailing.model.driver.Driver
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.Region
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.VehicleType
import com.ridehailing.model.ride.Ride
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.math.BigDecimal
import java.util.UUID

/**
 * Tests for auto-assignment of rides after the 60-second buffer.
 */
@ExtendWith(MockitoExtension::class)
class RideAutoAssignTest {

  @Mock lateinit var rideMapper: RideMapper
  @Mock lateinit var tripMapper: TripMapper
  @Mock lateinit var fareService: FareService
  @Mock lateinit var driverService: DriverService
  @Mock lateinit var riderService: RiderService
  @Mock lateinit var tenantService: TenantService
  @Mock lateinit var redisTemplate: RedisTemplate<String, Any>
  @Mock lateinit var valueOps: ValueOperations<String, Any>
  @Mock lateinit var rideEventService: RideEventService

  @InjectMocks lateinit var rideService: RideService

  private val tenantId = UUID.randomUUID()

  // ── Helpers ───────────────────────────────────────────────────────────

  private fun buildRide(
    rideId: UUID = UUID.randomUUID(),
    pickupLat: Double = 19.0760,
    pickupLng: Double = 72.8777,
    regionId: Int? = null,
    vehicleTypeId: Int = VehicleType.ECONOMY.id
  ) = Ride(
    id = rideId, tenantId = tenantId, riderId = UUID.randomUUID(),
    status = IdName(RideStatus.REQUESTED.id), pickupLat = pickupLat, pickupLng = pickupLng,
    destinationLat = pickupLat + 0.02, destinationLng = pickupLng + 0.02,
    vehicleType = IdName(vehicleTypeId), estimatedFare = BigDecimal("150.00"),
    region = regionId?.let { IdName(it) },
    surgeMultiplier = BigDecimal("1.00")
  )

  private fun buildDriver(
    driverId: UUID = UUID.randomUUID(),
    regionId: Int? = null,
    vehicleTypeId: Int = VehicleType.ECONOMY.id
  ) = Driver(
    id = driverId, tenantId = tenantId, name = "Test Driver",
    phone = "9876543210", vehicleType = IdName(vehicleTypeId),
    licensePlate = "MH01AB1234", status = IdName(DriverStatus.ONLINE.id),
    region = regionId?.let { IdName(it) }
  )

  // ── Scenario 1: Successful auto-assignment ───────────────────────────

  @Nested
  @DisplayName("Scenario 1: Successful auto-assignment")
  inner class SuccessfulAutoAssign {

    @Test
    @DisplayName("Nearest driver is auto-assigned when no one accepts within buffer")
    fun `autoAssignDriver - assigns nearest driver`() {
      val rideId = UUID.randomUUID()
      val driverId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId)
      val driver = buildDriver(driverId = driverId)

      whenever(driverService.findNearbyAvailable(
        eq(ride.pickupLat), eq(ride.pickupLng), eq(VehicleType.ECONOMY.id),
        eq(Constant.DriverMatching.AUTO_ASSIGN_RADIUS_KM)
      )).thenReturn(listOf(driver))

      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), eq(RideStatus.DRIVER_ACCEPTED.id))).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(acceptedRide)

      val result = rideService.autoAssignDriver(ride)

      assertTrue(result)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), eq(RideStatus.DRIVER_ACCEPTED.id))
      verify(tripMapper).insert(any())
      verify(driverService).updateStatus(eq(driverId), eq(DriverStatus.ON_TRIP.id))
      verify(rideEventService).broadcastRideUpdate(any())
    }

    @Test
    @DisplayName("First driver in sorted list (closest) is picked")
    fun `autoAssignDriver - picks closest driver from list`() {
      val rideId = UUID.randomUUID()
      val closestId = UUID.randomUUID()
      val fartherId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId)

      val closest = buildDriver(driverId = closestId)
      val farther = buildDriver(driverId = fartherId)

      // findNearbyAvailable returns sorted by distance (closest first)
      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(closest, farther))

      whenever(rideMapper.assignDriver(eq(rideId), eq(closestId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = closestId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(acceptedRide)

      val result = rideService.autoAssignDriver(ride)

      assertTrue(result)
      // Verify closest driver was assigned, not the farther one
      verify(rideMapper).assignDriver(eq(rideId), eq(closestId), any())
      verify(rideMapper, never()).assignDriver(eq(rideId), eq(fartherId), any())
    }

    @Test
    @DisplayName("Trip is created with correct ride and driver data")
    fun `autoAssignDriver - creates trip with correct data`() {
      val rideId = UUID.randomUUID()
      val driverId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = Region.MUMBAI.id)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(driver))
      whenever(rideMapper.assignDriver(any(), any(), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(acceptedRide)

      rideService.autoAssignDriver(ride)

      verify(tripMapper).insert(argThat {
        this.rideId == rideId &&
          this.driverId == driverId &&
          this.riderId == ride.riderId &&
          this.startLat == ride.pickupLat &&
          this.startLng == ride.pickupLng
      })
    }

    @Test
    @DisplayName("Cache is invalidated after auto-assignment")
    fun `autoAssignDriver - invalidates ride cache`() {
      val rideId = UUID.randomUUID()
      val driverId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId)
      val driver = buildDriver(driverId = driverId)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(driver))
      whenever(rideMapper.assignDriver(any(), any(), any())).thenReturn(1)
      whenever(rideMapper.findById(rideId)).thenReturn(
        ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      )

      rideService.autoAssignDriver(ride)

      verify(redisTemplate).delete("${Constant.Redis.RIDE_CACHE_KEY}$rideId")
    }
  }

  // ── Scenario 2: No drivers available ──────────────────────────────────

  @Nested
  @DisplayName("Scenario 2: No drivers available for auto-assignment")
  inner class NoDriversAvailable {

    @Test
    @DisplayName("Returns false when no nearby drivers exist")
    fun `autoAssignDriver - no nearby drivers returns false`() {
      val ride = buildRide()

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(emptyList())

      val result = rideService.autoAssignDriver(ride)

      assertFalse(result)
      verify(rideMapper, never()).assignDriver(any(), any(), any())
      verify(tripMapper, never()).insert(any())
    }

    @Test
    @DisplayName("Returns false when drivers exist but all are in wrong region")
    fun `autoAssignDriver - all drivers wrong region returns false`() {
      val ride = buildRide(regionId = Region.MUMBAI.id)
      val delhiDriver = buildDriver(regionId = Region.DELHI.id)
      val bangaloreDriver = buildDriver(regionId = Region.BANGALORE.id)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(delhiDriver, bangaloreDriver))

      val result = rideService.autoAssignDriver(ride)

      assertFalse(result)
      verify(rideMapper, never()).assignDriver(any(), any(), any())
    }
  }

  // ── Scenario 3: Region filtering during auto-assignment ───────────────

  @Nested
  @DisplayName("Scenario 3: Region filtering in auto-assignment")
  inner class RegionFiltering {

    @Test
    @DisplayName("Mumbai ride skips Delhi driver, assigns Mumbai driver")
    fun `autoAssignDriver - filters by region and assigns matching driver`() {
      val rideId = UUID.randomUUID()
      val mumbaiDriverId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)

      // Delhi driver is closer (first in list) but wrong region
      val delhiDriver = buildDriver(driverId = UUID.randomUUID(), regionId = Region.DELHI.id)
      val mumbaiDriver = buildDriver(driverId = mumbaiDriverId, regionId = Region.MUMBAI.id)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(delhiDriver, mumbaiDriver))

      whenever(rideMapper.assignDriver(eq(rideId), eq(mumbaiDriverId), any())).thenReturn(1)
      whenever(rideMapper.findById(rideId)).thenReturn(
        ride.copy(driverId = mumbaiDriverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      )

      val result = rideService.autoAssignDriver(ride)

      assertTrue(result)
      verify(rideMapper).assignDriver(eq(rideId), eq(mumbaiDriverId), any())
    }

    @Test
    @DisplayName("Driver with no region is eligible for any regional ride")
    fun `autoAssignDriver - regionless driver eligible for regional ride`() {
      val rideId = UUID.randomUUID()
      val driverId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = null) // no region

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(driver))
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)
      whenever(rideMapper.findById(rideId)).thenReturn(
        ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      )

      val result = rideService.autoAssignDriver(ride)

      assertTrue(result)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), any())
    }

    @Test
    @DisplayName("Ride with no region accepts any driver regardless of driver region")
    fun `autoAssignDriver - regionless ride accepts any driver`() {
      val rideId = UUID.randomUUID()
      val driverId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId, regionId = null)
      val driver = buildDriver(driverId = driverId, regionId = Region.DELHI.id)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(driver))
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)
      whenever(rideMapper.findById(rideId)).thenReturn(
        ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      )

      val result = rideService.autoAssignDriver(ride)

      assertTrue(result)
    }
  }

  // ── Scenario 4: Concurrent modification during auto-assignment ────────

  @Nested
  @DisplayName("Scenario 4: Concurrent modification")
  inner class ConcurrentModification {

    @Test
    @DisplayName("Returns false if ride was already accepted by a driver manually")
    fun `autoAssignDriver - ride already taken returns false`() {
      val rideId = UUID.randomUUID()
      val driverId = UUID.randomUUID()
      val ride = buildRide(rideId = rideId)
      val driver = buildDriver(driverId = driverId)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(driver))
      // assignDriver returns 0 — ride was already accepted by someone else
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(0)

      val result = rideService.autoAssignDriver(ride)

      assertFalse(result)
      verify(tripMapper, never()).insert(any())
      verify(driverService, never()).updateStatus(any(), eq(DriverStatus.ON_TRIP.id))
    }

    @Test
    @DisplayName("Does not broadcast update if assignment fails")
    fun `autoAssignDriver - no broadcast on failed assignment`() {
      val ride = buildRide()
      val driver = buildDriver()

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(listOf(driver))
      whenever(rideMapper.assignDriver(any(), any(), any())).thenReturn(0)

      rideService.autoAssignDriver(ride)

      verify(rideEventService, never()).broadcastRideUpdate(any())
    }
  }

  // ── Scenario 5: findUnacceptedRides ───────────────────────────────────

  @Nested
  @DisplayName("Scenario 5: Finding unaccepted rides")
  inner class FindUnacceptedRides {

    @Test
    @DisplayName("Delegates to mapper with correct buffer seconds")
    fun `findUnacceptedRides - passes buffer constant to mapper`() {
      whenever(rideMapper.findUnacceptedRides(Constant.DriverMatching.AUTO_ASSIGN_BUFFER_SECONDS))
        .thenReturn(emptyList())

      val result = rideService.findUnacceptedRides()

      assertTrue(result.isEmpty())
      verify(rideMapper).findUnacceptedRides(eq(60L))
    }

    @Test
    @DisplayName("Returns rides from mapper")
    fun `findUnacceptedRides - returns rides from mapper`() {
      val ride1 = buildRide()
      val ride2 = buildRide()

      whenever(rideMapper.findUnacceptedRides(any())).thenReturn(listOf(ride1, ride2))

      val result = rideService.findUnacceptedRides()

      assertEquals(2, result.size)
    }
  }

  // ── Scenario 6: Auto-assign radius uses correct constant ──────────────

  @Nested
  @DisplayName("Scenario 6: Configuration constants")
  inner class ConfigConstants {

    @Test
    @DisplayName("Uses AUTO_ASSIGN_RADIUS_KM (10km) not ACCEPT_RADIUS_KM (15km)")
    fun `autoAssignDriver - uses auto-assign radius constant`() {
      val ride = buildRide()

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(emptyList())

      rideService.autoAssignDriver(ride)

      verify(driverService).findNearbyAvailable(
        any(), any(), any(),
        eq(Constant.DriverMatching.AUTO_ASSIGN_RADIUS_KM) // 10.0, not 15.0
      )
    }

    @Test
    @DisplayName("Searches with correct vehicle type from ride")
    fun `autoAssignDriver - passes ride vehicle type`() {
      val ride = buildRide(vehicleTypeId = VehicleType.SUV.id)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(emptyList())

      rideService.autoAssignDriver(ride)

      verify(driverService).findNearbyAvailable(
        any(), any(),
        eq(VehicleType.SUV.id),
        any()
      )
    }

    @Test
    @DisplayName("Searches at ride pickup coordinates")
    fun `autoAssignDriver - searches at pickup location`() {
      val ride = buildRide(pickupLat = 28.6315, pickupLng = 77.2167)

      whenever(driverService.findNearbyAvailable(any(), any(), any(), any()))
        .thenReturn(emptyList())

      rideService.autoAssignDriver(ride)

      verify(driverService).findNearbyAvailable(
        eq(28.6315), eq(77.2167), any(), any()
      )
    }
  }

  // ── Scenario 7: Scheduler service ─────────────────────────────────────

  @Nested
  @DisplayName("Scenario 7: RideAutoAssignService scheduler")
  inner class SchedulerService {

    @Mock lateinit var mockRideService: RideService

    @Test
    @DisplayName("Does nothing when no unaccepted rides")
    fun `autoAssignUnacceptedRides - no rides does nothing`() {
      val scheduler = RideAutoAssignService(mockRideService)
      whenever(mockRideService.findUnacceptedRides()).thenReturn(emptyList())

      scheduler.autoAssignUnacceptedRides()

      verify(mockRideService, never()).autoAssignDriver(any())
    }

    @Test
    @DisplayName("Calls autoAssignDriver for each unaccepted ride")
    fun `autoAssignUnacceptedRides - processes each ride`() {
      val scheduler = RideAutoAssignService(mockRideService)
      val ride1 = buildRide()
      val ride2 = buildRide()

      whenever(mockRideService.findUnacceptedRides()).thenReturn(listOf(ride1, ride2))
      whenever(mockRideService.autoAssignDriver(any())).thenReturn(true)

      scheduler.autoAssignUnacceptedRides()

      verify(mockRideService).autoAssignDriver(ride1)
      verify(mockRideService).autoAssignDriver(ride2)
    }

    @Test
    @DisplayName("Continues processing remaining rides if one fails with exception")
    fun `autoAssignUnacceptedRides - exception on one ride does not stop others`() {
      val scheduler = RideAutoAssignService(mockRideService)
      val ride1 = buildRide()
      val ride2 = buildRide()

      whenever(mockRideService.findUnacceptedRides()).thenReturn(listOf(ride1, ride2))
      whenever(mockRideService.autoAssignDriver(ride1)).thenThrow(RuntimeException("DB error"))
      whenever(mockRideService.autoAssignDriver(ride2)).thenReturn(true)

      // Should not throw
      scheduler.autoAssignUnacceptedRides()

      // ride2 should still be processed despite ride1 failing
      verify(mockRideService).autoAssignDriver(ride2)
    }

    @Test
    @DisplayName("Handles autoAssignDriver returning false gracefully")
    fun `autoAssignUnacceptedRides - false return is handled gracefully`() {
      val scheduler = RideAutoAssignService(mockRideService)
      val ride = buildRide()

      whenever(mockRideService.findUnacceptedRides()).thenReturn(listOf(ride))
      whenever(mockRideService.autoAssignDriver(ride)).thenReturn(false)

      // Should not throw
      scheduler.autoAssignUnacceptedRides()

      verify(mockRideService).autoAssignDriver(ride)
    }
  }
}
