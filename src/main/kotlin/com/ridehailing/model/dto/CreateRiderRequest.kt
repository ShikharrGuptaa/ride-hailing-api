package com.ridehailing.model.dto

import com.ridehailing.config.Constant
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreateRiderRequest(
  @field:NotBlank val name: String,
  @field:NotBlank
  @field:Pattern(regexp = Constant.Validation.INDIAN_MOBILE_REGEX, message = "Invalid Indian mobile number")
  val phone: String,
  @field:Email(message = "Invalid email address")
  val email: String? = null,
  val regionId: Int? = null
)
