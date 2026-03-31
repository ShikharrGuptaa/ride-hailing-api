package com.ridehailing.controller

import com.ridehailing.model.common.ApiResponse
import com.ridehailing.model.ride.Ride
import com.ridehailing.model.dto.CreateRideRequest
import com.ridehailing.service.RideService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/rides")
class RideController(
  private val rideService: RideService
) {

  private val log = LoggerFactory.getLogger(RideController::class.java)

  @Operation(summary = "Create a ride request")
  @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  fun createRide(@Valid @RequestBody request: CreateRideRequest): ApiResponse<Ride> {
    log.info("createRide - POST /rides for rider: ${request.riderId}")
    val ride = rideService.createRide(request)
    return ApiResponse.ok(ride, "Ride created successfully")
  }

  @Operation(summary = "Get available rides for drivers")
  @GetMapping(value = ["/available"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getAvailableRides(
    @RequestParam vehicleTypeId: Int,
    @RequestParam driverLat: Double,
    @RequestParam driverLng: Double,
    @RequestParam(required = false) regionId: Int?
  ): ApiResponse<List<Ride>> {
    log.info("getAvailableRides - GET /rides/available?vehicleTypeId=$vehicleTypeId&driverLat=$driverLat&driverLng=$driverLng&regionId=$regionId")
    return ApiResponse.ok(rideService.findAvailableRides(vehicleTypeId, driverLat, driverLng, regionId))
  }

  @Operation(summary = "Estimate fare for a ride")
  @GetMapping(value = ["/estimate"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun estimateFare(
    @RequestParam pickupLat: Double, @RequestParam pickupLng: Double,
    @RequestParam destLat: Double, @RequestParam destLng: Double,
    @RequestParam vehicleTypeId: Int
  ): ApiResponse<Map<String, Any>> {
    log.info("estimateFare - GET /rides/estimate")
    val vehicleType = com.ridehailing.model.enums.VehicleType.entries.first { it.id == vehicleTypeId }
    val fareService = rideService.getFareService()
    val distance = fareService.haversineDistance(pickupLat, pickupLng, destLat, destLng)
    val durationMin = distance.divide(java.math.BigDecimal(25), 2, java.math.RoundingMode.HALF_UP)
      .multiply(java.math.BigDecimal(60)).setScale(2, java.math.RoundingMode.HALF_UP)
    val breakdown = fareService.calculateFare(distance, durationMin, vehicleType)
    return ApiResponse.ok(mapOf(
      "estimatedFare" to breakdown.totalFare,
      "baseFare" to breakdown.baseFare,
      "distanceFare" to breakdown.distanceFare,
      "timeFare" to breakdown.timeFare,
      "distanceKm" to distance,
      "durationMin" to durationMin,
      "surgeMultiplier" to breakdown.surgeMultiplier
    ))
  }

  @Operation(summary = "Get ride status")
  @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getRide(@PathVariable id: UUID): ApiResponse<Ride> {
    log.info("getRide - GET /rides/$id")
    return ApiResponse.ok(rideService.getRide(id))
  }

  @Operation(summary = "Cancel a ride")
  @PostMapping(value = ["/{id}/cancel"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun cancelRide(@PathVariable id: UUID, @RequestBody body: Map<String, String>): ApiResponse<Ride> {
    log.info("cancelRide - POST /rides/$id/cancel")
    val riderId = UUID.fromString(body["riderId"])
    return ApiResponse.ok(rideService.cancelRide(id, riderId), "Ride cancelled successfully")
  }
}
