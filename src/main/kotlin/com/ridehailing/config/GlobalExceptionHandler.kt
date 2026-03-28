package com.ridehailing.config

import com.ridehailing.model.ApiResponse
import com.ridehailing.model.ApplicationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

  private val log = LoggerFactory.getLogger(this::class.java)

  @ExceptionHandler(ApplicationException::class)
  fun handleApplicationException(e: ApplicationException): ResponseEntity<ApiResponse<Nothing>> {
    log.debug("handleApplicationException - code: ${e.code}, message: ${e.message}")
    return ResponseEntity.status(e.status)
      .body(ApiResponse.error(e.code, e.message))
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
    val errors = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
    log.warn("handleValidation - $errors")
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(ApiResponse.error(27, errors))
  }

  @ExceptionHandler(Exception::class)
  fun handleGeneric(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
    log.error("handleGeneric - Unexpected error", e)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ApiResponse.error(99, "Something went wrong. Please try again later."))
  }
}
