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
  fun getAvailableRides(@RequestParam vehicleTypeId: Int): ApiResponse<List<Ride>> {
    log.info("getAvailableRides - GET /rides/available?vehicleTypeId=$vehicleTypeId")
    return ApiResponse.ok(rideService.findAvailableRides(vehicleTypeId))
  }

  @Operation(summary = "Get ride status")
  @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getRide(@PathVariable id: UUID): ApiResponse<Ride> {
    log.info("getRide - GET /rides/$id")
    return ApiResponse.ok(rideService.getRide(id))
  }
}
