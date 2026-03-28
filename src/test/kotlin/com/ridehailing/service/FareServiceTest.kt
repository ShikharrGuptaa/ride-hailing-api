package com.ridehailing.service

import com.ridehailing.model.enums.VehicleType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FareServiceTest {

  private val fareService = FareService()

  @Test
  fun `calculateFare - economy fare with no surge`() {
    val result = fareService.calculateFare(
      distanceKm = BigDecimal("10.000"),
      durationMinutes = BigDecimal("25.00"),
      vehicleType = VehicleType.ECONOMY
    )

    assertEquals(BigDecimal("30"), result.baseFare)
    assertEquals(BigDecimal("120.00"), result.distanceFare) // 12 * 10
    assertEquals(BigDecimal("37.50"), result.timeFare) // 1.5 * 25
    assertEquals(BigDecimal("187.50"), result.totalFare) // 30 + 120 + 37.5
    assertEquals(BigDecimal.ONE, result.surgeMultiplier)
  }

  @Test
  fun `calculateFare - premium fare with surge`() {
    val result = fareService.calculateFare(
      distanceKm = BigDecimal("5.000"),
      durationMinutes = BigDecimal("15.00"),
      vehicleType = VehicleType.PREMIUM,
      surgeMultiplier = BigDecimal("1.50")
    )

    assertEquals(BigDecimal("50"), result.baseFare)
    assertEquals(BigDecimal("90.00"), result.distanceFare) // 18 * 5
    assertEquals(BigDecimal("37.50"), result.timeFare) // 2.5 * 15
    // (50 + 90 + 37.5) * 1.5 = 266.25
    assertEquals(BigDecimal("266.25"), result.totalFare)
  }

  @Test
  fun `calculateFare - SUV fare`() {
    val result = fareService.calculateFare(
      distanceKm = BigDecimal("8.000"),
      durationMinutes = BigDecimal("20.00"),
      vehicleType = VehicleType.SUV
    )

    assertEquals(BigDecimal("70"), result.baseFare)
    assertEquals(BigDecimal("176.00"), result.distanceFare) // 22 * 8
    assertEquals(BigDecimal("60.00"), result.timeFare) // 3.0 * 20
    assertEquals(BigDecimal("306.00"), result.totalFare)
  }

  @Test
  fun `haversineDistance - known distance Mumbai to Pune`() {
    // Mumbai: 19.076, 72.877 | Pune: 18.520, 73.856
    val distance = fareService.haversineDistance(19.076, 72.877, 18.520, 73.856)
    // Should be roughly 120-130 km
    assertTrue(distance.toDouble() > 100)
    assertTrue(distance.toDouble() < 150)
  }

  @Test
  fun `haversineDistance - same point returns zero`() {
    val distance = fareService.haversineDistance(19.076, 72.877, 19.076, 72.877)
    assertEquals(BigDecimal("0.000"), distance)
  }

  @Test
  fun `estimateFare - returns positive fare for valid coordinates`() {
    val fare = fareService.estimateFare(
      19.076, 72.877, // Mumbai
      19.100, 72.900, // Nearby
      VehicleType.ECONOMY
    )
    assertTrue(fare > BigDecimal.ZERO)
  }
}
