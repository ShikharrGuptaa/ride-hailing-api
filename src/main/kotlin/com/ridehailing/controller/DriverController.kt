package com.ridehailing.controller

import com.ridehailing.model.common.ApiResponse
import com.ridehailing.model.driver.Driver
import com.ridehailing.model.driver.DriverCurrentLocation
import com.ridehailing.model.ride.Ride
import com.ridehailing.model.dto.AcceptRideRequest
import com.ridehailing.model.dto.AuthResponse
import com.ridehailing.model.dto.CreateDriverRequest
import com.ridehailing.model.dto.UpdateLocationRequest
import com.ridehailing.service.DriverService
import com.ridehailing.service.JwtService
import com.ridehailing.service.RideService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/drivers")
class DriverController(
  private val driverService: DriverService,
  private val rideService: RideService,
  private val jwtService: JwtService
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  @Operation(summary = "Register a new driver")
  @ApiResponses(
    value = [
      io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", description = "Driver registered",
        content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)]
      )
    ]
  )
  @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  fun createDriver(@Valid @RequestBody request: CreateDriverRequest): ApiResponse<AuthResponse<Driver>> {
    log.info("createDriver - POST /drivers")
    val driver = driverService.createDriver(request)
    val token = jwtService.generateToken(driver.id!!, "DRIVER")
    return ApiResponse.ok(AuthResponse(token, driver), "Driver registered successfully")
  }

  @Operation(summary = "Lookup driver by phone (login)")
  @GetMapping(value = ["/lookup"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun lookupByPhone(@RequestParam phone: String): ApiResponse<AuthResponse<Driver>?> {
    log.info("lookupByPhone - GET /drivers/lookup?phone=$phone")
    val driver = driverService.findByPhone(phone)
    if (driver != null) {
      val token = jwtService.generateToken(driver.id!!, "DRIVER")
      return ApiResponse.ok(AuthResponse(token, driver))
    }
    return ApiResponse.ok(null, "Not found")
  }

  @Operation(summary = "Get driver earnings")
  @GetMapping(value = ["/{id}/earnings"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getEarnings(@PathVariable id: UUID): ApiResponse<Map<String, Any>?> {
    log.info("getEarnings - GET /drivers/$id/earnings")
    return ApiResponse.ok(rideService.getDriverEarnings(id))
  }

  @Operation(summary = "Get driver by ID")
  @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getDriver(@PathVariable id: UUID): ApiResponse<Driver> {
    log.info("getDriver - GET /drivers/$id")
    return ApiResponse.ok(driverService.findById(id))
  }

  @Operation(summary = "Update driver location")
  @PostMapping(value = ["/{id}/location"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun updateLocation(
    @PathVariable id: UUID,
    @Valid @RequestBody request: UpdateLocationRequest
  ): ApiResponse<DriverCurrentLocation> {
    log.info("updateLocation - POST /drivers/$id/location")
    val location = driverService.updateLocation(id, request.lat, request.lng)
    return ApiResponse.ok(location, "Location updated")
  }

  @Operation(summary = "Accept a ride assignment")
  @PostMapping(value = ["/{id}/accept"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun acceptRide(
    @PathVariable id: UUID,
    @Valid @RequestBody request: AcceptRideRequest
  ): ApiResponse<Ride> {
    log.info("acceptRide - POST /drivers/$id/accept for ride: ${request.rideId}")
    val ride = rideService.acceptRide(id, request.rideId)
    return ApiResponse.ok(ride, "Ride accepted")
  }

  @Operation(summary = "Update driver status (ONLINE/OFFLINE)")
  @PostMapping(value = ["/{id}/status"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun updateStatus(
    @PathVariable id: UUID,
    @Valid @RequestBody request: com.ridehailing.model.dto.UpdateStatusRequest
  ): ApiResponse<String> {
    log.info("updateStatus - POST /drivers/$id/status to ${request.statusId}")
    driverService.updateStatus(id, request.statusId)
    return ApiResponse.ok("updated", "Driver status updated")
  }

  @Operation(summary = "Switch driver vehicle type")
  @PostMapping(value = ["/{id}/vehicle"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun updateVehicleType(
    @PathVariable id: UUID,
    @RequestBody body: Map<String, Any>
  ): ApiResponse<Driver> {
    log.info("updateVehicleType - POST /drivers/$id/vehicle")
    val vehicleTypeId = (body["vehicleTypeId"] as Number).toInt()
    val licensePlate = body["licensePlate"] as? String
    return ApiResponse.ok(driverService.updateVehicleType(id, vehicleTypeId, licensePlate), "Vehicle type updated")
  }

  @Operation(summary = "Get active ride assigned to driver")
  @GetMapping(value = ["/{id}/active-ride"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getActiveRide(@PathVariable id: UUID): ApiResponse<Ride?> {
    log.debug("getActiveRide - GET /drivers/$id/active-ride")
    val ride = rideService.getActiveRideForDriver(id)
    return ApiResponse.ok(ride)
  }
}
