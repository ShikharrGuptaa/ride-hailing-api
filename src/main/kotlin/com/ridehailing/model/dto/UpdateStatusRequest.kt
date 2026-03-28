package com.ridehailing.model.dto

import jakarta.validation.constraints.NotNull

data class UpdateStatusRequest(
  @field:NotNull val statusId: Int
)
