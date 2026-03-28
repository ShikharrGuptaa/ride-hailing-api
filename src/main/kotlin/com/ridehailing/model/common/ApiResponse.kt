package com.ridehailing.model.common

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
  val success: Boolean,
  val data: T? = null,
  val message: String? = null,
  val error: ErrorDetail? = null
) {
  companion object {
    fun <T> ok(data: T, message: String? = null) =
      ApiResponse(success = true, data = data, message = message)

    fun error(code: Int, message: String) =
      ApiResponse<Nothing>(success = false, error = ErrorDetail(code, message))
  }
}

data class ErrorDetail(
  val code: Int,
  val message: String
)
