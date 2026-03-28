package com.ridehailing.controller

import com.ridehailing.model.common.ApiResponse
import com.ridehailing.model.rider.Rider
import com.ridehailing.model.dto.AuthResponse
import com.ridehailing.model.dto.CreateRiderRequest
import com.ridehailing.service.JwtService
import com.ridehailing.service.RiderService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/riders")
class RiderController(
  private val riderService: RiderService,
  private val jwtService: JwtService
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  @Operation(summary = "Register/login rider (returns JWT)")
  @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  fun createRider(@Valid @RequestBody request: CreateRiderRequest): ApiResponse<AuthResponse<Rider>> {
    log.info("createRider - POST /riders")
    val rider = riderService.createRider(request)
    val token = jwtService.generateToken(rider.id!!, "RIDER")
    return ApiResponse.ok(AuthResponse(token, rider), "Rider registered successfully")
  }

  @Operation(summary = "Lookup rider by phone (login)")
  @GetMapping(value = ["/lookup"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun lookupByPhone(@RequestParam phone: String): ApiResponse<AuthResponse<Rider>?> {
    log.info("lookupByPhone - GET /riders/lookup?phone=$phone")
    val rider = riderService.findByPhone(phone)
    if (rider != null) {
      val token = jwtService.generateToken(rider.id!!, "RIDER")
      return ApiResponse.ok(AuthResponse(token, rider))
    }
    return ApiResponse.ok(null, "Not found")
  }

  @Operation(summary = "Get rider by ID")
  @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getRider(@PathVariable id: UUID): ApiResponse<Rider> {
    log.info("getRider - GET /riders/$id")
    return ApiResponse.ok(riderService.findById(id))
  }
}
