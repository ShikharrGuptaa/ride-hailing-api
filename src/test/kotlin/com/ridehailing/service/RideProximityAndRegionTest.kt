package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.config.Constant
import com.ridehailing.mapper.RideMapper
import com.ridehailing.mapper.TripMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.driver.Driver
import com.ridehailing.model.driver.DriverCurrentLocation
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.Region
import com.ridehailing.model.enums.RideStatus
import com.ridehailing.model.enums.VehicleType
import com.ridehailing.model.ride.Ride
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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

/**
 * Scenario-based tests for proximity-based ride visibility and region-based filtering.
 *
 * Coordinate reference points used in tests:
 * - Mumbai CST:       19.0760, 72.8777
 * - Mumbai Andheri:   19.1197, 72.8464  (~5.3km from CST)
 * - Mumbai Thane:     19.2183, 72.9781  (~18km from CST)
 * - Delhi Connaught:  28.6315, 77.2167  (~1,150km from Mumbai)
 * - Bangalore MG Rd:  12.9716, 77.5946  (~845km from Mumbai)
 */
@ExtendWith(MockitoExtension::class)
class RideProximityAndRegionTest {

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

  // ── Helper builders ──────────────────────────────────────────────────

  private fun buildRide(
    rideId: UUID = UUID.randomUUID(),
    pickupLat: Double = 19.0760,
    pickupLng: Double = 72.8777,
    regionId: Int? = null,
    statusId: Int = RideStatus.REQUESTED.id,
    vehicleTypeId: Int = VehicleType.ECONOMY.id
  ) = Ride(
    id = rideId, tenantId = tenantId, riderId = UUID.randomUUID(),
    status = IdName(statusId), pickupLat = pickupLat, pickupLng = pickupLng,
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

  private fun buildLocation(driverId: UUID, lat: Double, lng: Double) =
    DriverCurrentLocation(driverId = driverId, lat = lat, lng = lng)

  // ── Scenario 1: Proximity-based ride visibility (findAvailableRides) ─

  @Nested
  @DisplayName("Scenario 1: Proximity-based ride visibility")
  inner class ProximityVisibility {

    @Test
    @DisplayName("Driver in Mumbai CST sees rides within 10km radius")
    fun `findAvailableRides - passes driver location and radius to mapper`() {
      // Driver at Mumbai CST, looking for Economy rides
      val driverLat = 19.0760
      val driverLng = 72.8777

      val nearbyRide = buildRide(pickupLat = 19.0900, pickupLng = 72.8800) // ~1.6km away
      whenever(rideMapper.findAvailableByVehicleType(
        eq(VehicleType.ECONOMY.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), isNull()
      )).thenReturn(listOf(nearbyRide))

      val result = rideService.findAvailableRides(VehicleType.ECONOMY.id, driverLat, driverLng, null)

      assertEquals(1, result.size)
      assertEquals(nearbyRide.id, result[0].id)
      verify(rideMapper).findAvailableByVehicleType(
        eq(VehicleType.ECONOMY.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), isNull()
      )
    }

    @Test
    @DisplayName("Driver in Delhi sees no Mumbai rides (>1000km away)")
    fun `findAvailableRides - distant driver gets empty list`() {
      // Driver in Delhi, no Mumbai rides should appear
      val driverLat = 28.6315
      val driverLng = 77.2167

      whenever(rideMapper.findAvailableByVehicleType(
        eq(VehicleType.ECONOMY.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), isNull()
      )).thenReturn(emptyList())

      val result = rideService.findAvailableRides(VehicleType.ECONOMY.id, driverLat, driverLng, null)

      assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("Driver at Andheri sees CST ride (~5km) but not Thane ride (~18km)")
    fun `findAvailableRides - only rides within radius returned`() {
      // Driver at Andheri, ride at CST is ~5km (within 10km), ride at Thane is ~18km (outside)
      val driverLat = 19.1197
      val driverLng = 72.8464

      val cstRide = buildRide(pickupLat = 19.0760, pickupLng = 72.8777) // ~5.3km
      // Thane ride would be filtered out by SQL, so mapper returns only CST ride
      whenever(rideMapper.findAvailableByVehicleType(
        eq(VehicleType.ECONOMY.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), isNull()
      )).thenReturn(listOf(cstRide))

      val result = rideService.findAvailableRides(VehicleType.ECONOMY.id, driverLat, driverLng, null)

      assertEquals(1, result.size)
      assertEquals(cstRide.id, result[0].id)
    }

    @Test
    @DisplayName("Visibility radius uses configured constant (10km)")
    fun `findAvailableRides - uses RIDE_VISIBILITY_RADIUS_KM constant`() {
      val driverLat = 19.0760
      val driverLng = 72.8777

      whenever(rideMapper.findAvailableByVehicleType(
        any(), any(), any(), any(), anyOrNull()
      )).thenReturn(emptyList())

      rideService.findAvailableRides(VehicleType.ECONOMY.id, driverLat, driverLng, null)

      // Verify the exact radius constant is passed
      verify(rideMapper).findAvailableByVehicleType(
        any(), any(), any(),
        eq(10.0), // RIDE_VISIBILITY_RADIUS_KM
        anyOrNull()
      )
    }
  }

  // ── Scenario 2: Region-based filtering (findAvailableRides) ──────────

  @Nested
  @DisplayName("Scenario 2: Region-based ride filtering")
  inner class RegionFiltering {

    @Test
    @DisplayName("Mumbai driver passes regionId=701 to filter rides by region")
    fun `findAvailableRides - passes regionId to mapper`() {
      val driverLat = 19.0760
      val driverLng = 72.8777

      val mumbaiRide = buildRide(regionId = Region.MUMBAI.id)
      whenever(rideMapper.findAvailableByVehicleType(
        eq(VehicleType.ECONOMY.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), eq(Region.MUMBAI.id)
      )).thenReturn(listOf(mumbaiRide))

      val result = rideService.findAvailableRides(
        VehicleType.ECONOMY.id, driverLat, driverLng, Region.MUMBAI.id
      )

      assertEquals(1, result.size)
      verify(rideMapper).findAvailableByVehicleType(
        any(), any(), any(), any(), eq(Region.MUMBAI.id)
      )
    }

    @Test
    @DisplayName("Driver with no region sees all nearby rides (regionId=null)")
    fun `findAvailableRides - null regionId does not filter by region`() {
      val driverLat = 19.0760
      val driverLng = 72.8777

      val ride1 = buildRide(regionId = Region.MUMBAI.id)
      val ride2 = buildRide(regionId = null)
      whenever(rideMapper.findAvailableByVehicleType(
        eq(VehicleType.ECONOMY.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), isNull()
      )).thenReturn(listOf(ride1, ride2))

      val result = rideService.findAvailableRides(
        VehicleType.ECONOMY.id, driverLat, driverLng, null
      )

      assertEquals(2, result.size)
    }

    @Test
    @DisplayName("Delhi driver with regionId=702 does not see Mumbai rides")
    fun `findAvailableRides - different region returns empty`() {
      val driverLat = 28.6315
      val driverLng = 77.2167

      whenever(rideMapper.findAvailableByVehicleType(
        eq(VehicleType.ECONOMY.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), eq(Region.DELHI.id)
      )).thenReturn(emptyList())

      val result = rideService.findAvailableRides(
        VehicleType.ECONOMY.id, driverLat, driverLng, Region.DELHI.id
      )

      assertTrue(result.isEmpty())
    }
  }

  // ── Scenario 3: Proximity validation on acceptRide ───────────────────

  @Nested
  @DisplayName("Scenario 3: Proximity check when driver accepts a ride")
  inner class AcceptRideProximity {

    @Test
    @DisplayName("Driver 5km from pickup can accept the ride")
    fun `acceptRide - nearby driver succeeds`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      // Ride at Mumbai CST
      val ride = buildRide(rideId = rideId, pickupLat = 19.0760, pickupLng = 72.8777)
      val driver = buildDriver(driverId = driverId)
      // Driver at Andheri (~5.3km from CST, within 15km accept radius)
      val driverLoc = buildLocation(driverId, 19.1197, 72.8464)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(ride, acceptedRide)

      val result = rideService.acceptRide(driverId, rideId)

      assertEquals(driverId, result.driverId)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), any())
      verify(tripMapper).insert(any())
    }

    @Test
    @DisplayName("Driver in Delhi cannot accept Mumbai ride (>1000km away)")
    fun `acceptRide - distant driver throws DRIVER_TOO_FAR`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      // Ride at Mumbai CST
      val ride = buildRide(rideId = rideId, pickupLat = 19.0760, pickupLng = 72.8777)
      val driver = buildDriver(driverId = driverId)
      // Driver in Delhi (~1150km away)
      val driverLoc = buildLocation(driverId, 28.6315, 77.2167)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.DRIVER_TOO_FAR.first, ex.code)
      verify(rideMapper, never()).assignDriver(any(), any(), any())
      verify(tripMapper, never()).insert(any())
    }

    @Test
    @DisplayName("Driver at Thane cannot accept CST ride (~18km, exceeds 15km limit)")
    fun `acceptRide - driver just outside accept radius is rejected`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      // Ride at Mumbai CST
      val ride = buildRide(rideId = rideId, pickupLat = 19.0760, pickupLng = 72.8777)
      val driver = buildDriver(driverId = driverId)
      // Driver at Thane (~18km from CST, exceeds 15km ACCEPT_RADIUS_KM)
      val driverLoc = buildLocation(driverId, 19.2183, 72.9781)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.DRIVER_TOO_FAR.first, ex.code)
      verify(rideMapper, never()).assignDriver(any(), any(), any())
    }

    @Test
    @DisplayName("Driver at exactly 14km from pickup can still accept")
    fun `acceptRide - driver within accept radius boundary succeeds`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      // Ride at Mumbai CST
      val ride = buildRide(rideId = rideId, pickupLat = 19.0760, pickupLng = 72.8777)
      val driver = buildDriver(driverId = driverId)
      // Driver ~14km north of CST (within 15km limit)
      val driverLoc = buildLocation(driverId, 19.2020, 72.8777)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(ride, acceptedRide)

      val result = rideService.acceptRide(driverId, rideId)

      assertNotNull(result)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), any())
    }

    @Test
    @DisplayName("Driver with no location record can still accept (graceful fallback)")
    fun `acceptRide - no location record skips proximity check`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId)
      val driver = buildDriver(driverId = driverId)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(null) // no location
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(ride, acceptedRide)

      val result = rideService.acceptRide(driverId, rideId)

      assertNotNull(result)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), any())
    }
  }

  // ── Scenario 4: Region validation on acceptRide ──────────────────────

  @Nested
  @DisplayName("Scenario 4: Region check when driver accepts a ride")
  inner class AcceptRideRegion {

    @Test
    @DisplayName("Mumbai driver can accept Mumbai ride (same region)")
    fun `acceptRide - matching region succeeds`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = Region.MUMBAI.id)
      val driverLoc = buildLocation(driverId, 19.0760, 72.8777) // same location as pickup

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(ride, acceptedRide)

      val result = rideService.acceptRide(driverId, rideId)

      assertEquals(driverId, result.driverId)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), any())
    }

    @Test
    @DisplayName("Delhi driver cannot accept Mumbai ride (region mismatch)")
    fun `acceptRide - mismatched region throws REGION_MISMATCH`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId, pickupLat = 19.0760, pickupLng = 72.8777, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = Region.DELHI.id)
      // Driver is nearby (to pass proximity check) but wrong region
      val driverLoc = buildLocation(driverId, 19.0760, 72.8777)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.REGION_MISMATCH.first, ex.code)
      verify(rideMapper, never()).assignDriver(any(), any(), any())
    }

    @Test
    @DisplayName("Bangalore driver cannot accept Hyderabad ride (region mismatch)")
    fun `acceptRide - bangalore driver rejected for hyderabad ride`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      // Ride in Hyderabad
      val ride = buildRide(
        rideId = rideId, pickupLat = 17.3850, pickupLng = 78.4867,
        regionId = Region.HYDERABAD.id
      )
      // Driver registered in Bangalore but physically near pickup (to isolate region check)
      val driver = buildDriver(driverId = driverId, regionId = Region.BANGALORE.id)
      val driverLoc = buildLocation(driverId, 17.3850, 78.4867)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.REGION_MISMATCH.first, ex.code)
    }

    @Test
    @DisplayName("Driver with no region can accept ride with any region")
    fun `acceptRide - driver without region accepts regional ride`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = null) // no region set
      val driverLoc = buildLocation(driverId, 19.0760, 72.8777)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(ride, acceptedRide)

      val result = rideService.acceptRide(driverId, rideId)

      assertNotNull(result)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), any())
    }

    @Test
    @DisplayName("Any driver can accept ride with no region set")
    fun `acceptRide - ride without region accepts any driver`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId, regionId = null) // no region on ride
      val driver = buildDriver(driverId = driverId, regionId = Region.DELHI.id)
      val driverLoc = buildLocation(driverId, 19.0760, 72.8777)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(ride, acceptedRide)

      val result = rideService.acceptRide(driverId, rideId)

      assertNotNull(result)
      verify(rideMapper).assignDriver(eq(rideId), eq(driverId), any())
    }
  }

  // ── Scenario 5: Combined proximity + region on acceptRide ────────────

  @Nested
  @DisplayName("Scenario 5: Both proximity and region enforced together")
  inner class CombinedProximityAndRegion {

    @Test
    @DisplayName("Nearby driver with correct region can accept")
    fun `acceptRide - both checks pass`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = Region.MUMBAI.id)
      val driverLoc = buildLocation(driverId, 19.0800, 72.8800) // ~0.5km away

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(1)

      val acceptedRide = ride.copy(driverId = driverId, status = IdName(RideStatus.DRIVER_ACCEPTED.id))
      whenever(rideMapper.findById(rideId)).thenReturn(ride, acceptedRide)

      val result = rideService.acceptRide(driverId, rideId)

      assertEquals(driverId, result.driverId)
    }

    @Test
    @DisplayName("Proximity fails first before region check (driver too far)")
    fun `acceptRide - proximity fails before region is checked`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      // Ride in Mumbai, driver in Delhi with Delhi region
      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = Region.DELHI.id)
      val driverLoc = buildLocation(driverId, 28.6315, 77.2167) // Delhi, ~1150km

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)

      // Should fail on proximity first (DRIVER_TOO_FAR), not REGION_MISMATCH
      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.DRIVER_TOO_FAR.first, ex.code)
    }

    @Test
    @DisplayName("Nearby driver with wrong region is rejected on region check")
    fun `acceptRide - proximity passes but region fails`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      // Ride in Mumbai, driver physically nearby but registered in Delhi
      val ride = buildRide(rideId = rideId, regionId = Region.MUMBAI.id)
      val driver = buildDriver(driverId = driverId, regionId = Region.DELHI.id)
      val driverLoc = buildLocation(driverId, 19.0760, 72.8777) // at pickup

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.REGION_MISMATCH.first, ex.code)
    }
  }

  // ── Scenario 6: Edge cases ───────────────────────────────────────────

  @Nested
  @DisplayName("Scenario 6: Edge cases")
  inner class EdgeCases {

    @Test
    @DisplayName("acceptRide still validates ride status before proximity/region")
    fun `acceptRide - non-REQUESTED ride rejected before proximity check`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId, statusId = RideStatus.COMPLETED.id)
      whenever(rideMapper.findById(rideId)).thenReturn(ride)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.INVALID_RIDE_STATUS.first, ex.code)
      // Should not even call driverService
      verify(driverService, never()).findById(any())
      verify(driverService, never()).findCurrentLocation(any())
    }

    @Test
    @DisplayName("acceptRide - ride not found throws before any checks")
    fun `acceptRide - ride not found`() {
      val rideId = UUID.randomUUID()
      whenever(rideMapper.findById(rideId)).thenReturn(null)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(UUID.randomUUID(), rideId)
      }

      assertEquals(ApplicationExceptionTypes.RIDE_NOT_FOUND.first, ex.code)
    }

    @Test
    @DisplayName("acceptRide - concurrent accept by another driver after proximity/region pass")
    fun `acceptRide - concurrent modification after validation passes`() {
      val driverId = UUID.randomUUID()
      val rideId = UUID.randomUUID()

      val ride = buildRide(rideId = rideId)
      val driver = buildDriver(driverId = driverId)
      val driverLoc = buildLocation(driverId, 19.0760, 72.8777)

      whenever(rideMapper.findById(rideId)).thenReturn(ride)
      whenever(driverService.findById(driverId)).thenReturn(driver)
      whenever(driverService.findCurrentLocation(driverId)).thenReturn(driverLoc)
      // Another driver already took it
      whenever(rideMapper.assignDriver(eq(rideId), eq(driverId), any())).thenReturn(0)

      val ex = assertThrows<ApplicationException> {
        rideService.acceptRide(driverId, rideId)
      }

      assertEquals(ApplicationExceptionTypes.CONCURRENT_MODIFICATION.first, ex.code)
    }

    @Test
    @DisplayName("findAvailableRides - different vehicle types are isolated")
    fun `findAvailableRides - vehicle type filtering still works`() {
      val driverLat = 19.0760
      val driverLng = 72.8777

      whenever(rideMapper.findAvailableByVehicleType(
        eq(VehicleType.PREMIUM.id), eq(driverLat), eq(driverLng),
        eq(Constant.DriverMatching.RIDE_VISIBILITY_RADIUS_KM), isNull()
      )).thenReturn(emptyList())

      val result = rideService.findAvailableRides(
        VehicleType.PREMIUM.id, driverLat, driverLng, null
      )

      assertTrue(result.isEmpty())
      verify(rideMapper).findAvailableByVehicleType(
        eq(VehicleType.PREMIUM.id), any(), any(), any(), anyOrNull()
      )
    }
  }
}
