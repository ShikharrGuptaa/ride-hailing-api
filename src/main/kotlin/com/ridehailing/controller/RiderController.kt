package com.ridehailing.controller

import com.ridehailing.model.ApiResponse
import com.ridehailing.model.Rider
import com.ridehailing.model.dto.CreateRiderRequest
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
  private val riderService: RiderService
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  @Operation(summary = "Register a new rider")
  @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  fun createRider(@Valid @RequestBody request: CreateRiderRequest): ApiResponse<Rider> {
    log.info("createRider - POST /riders")
    val rider = riderService.createRider(request)
    return ApiResponse.ok(rider, "Rider registered successfully")
  }

  @Operation(summary = "Get rider by ID")
  @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getRider(@PathVariable id: UUID): ApiResponse<Rider> {
    log.info("getRider - GET /riders/$id")
    return ApiResponse.ok(riderService.findById(id))
  }
}
