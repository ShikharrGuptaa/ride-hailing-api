package com.ridehailing.model.common

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus

@JsonIgnoreProperties("cause", "stackTrace", "status", "suppressed", "localizedMessage")
data class ApplicationException(
  var code: Int,
  var status: HttpStatus,
  override var message: String,
  var details: Any? = null
) : Exception() {

  constructor(triple: Triple<Int, HttpStatus, String>) : this(triple.first, triple.second, triple.third)

  constructor(triple: Triple<Int, HttpStatus, String>, details: Any?) : this(triple.first, triple.second, triple.third, details)
}
