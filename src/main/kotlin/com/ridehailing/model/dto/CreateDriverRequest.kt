package com.ridehailing.model.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import com.ridehailing.config.Constant

data class CreateDriverRequest(
  @field:NotBlank val name: String,
  @field:NotBlank
  @field:Pattern(regexp = Constant.Validation.INDIAN_MOBILE_REGEX, message = "Invalid Indian mobile number")
  val phone: String,
  @field:NotNull val vehicleTypeId: Int,
  @field:NotBlank val licensePlate: String,
  val regionId: Int? = null
)