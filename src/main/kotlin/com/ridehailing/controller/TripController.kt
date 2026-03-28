package com.ridehailing.controller

import com.ridehailing.model.common.ApiResponse
import com.ridehailing.model.trip.Trip
import com.ridehailing.model.dto.EndTripRequest
import com.ridehailing.service.TripService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/trips")
class TripController(
  private val tripService: TripService
) {

  private val log = LoggerFactory.getLogger(TripController::class.java)

  @Operation(summary = "End trip and calculate fare")
  @PostMapping(value = ["/{id}/end"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun endTrip(
    @PathVariable id: UUID,
    @Valid @RequestBody request: EndTripRequest
  ): ApiResponse<Trip> {
    log.info("endTrip - POST /trips/$id/end")
    val trip = tripService.endTrip(id, request.endLat, request.endLng)
    return ApiResponse.ok(trip, "Trip ended, fare calculated")
  }

  @Operation(summary = "Get trip details")
  @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getTrip(@PathVariable id: UUID): ApiResponse<Trip> {
    log.info("getTrip - GET /trips/$id")
    return ApiResponse.ok(tripService.getTrip(id))
  }
}
