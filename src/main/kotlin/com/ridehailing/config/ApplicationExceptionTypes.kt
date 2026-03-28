package com.ridehailing.config

import org.springframework.http.HttpStatus

object ApplicationExceptionTypes {
  val DRIVER_NOT_FOUND = Triple(1, HttpStatus.NOT_FOUND, "Driver not found")
  val RIDER_NOT_FOUND = Triple(2, HttpStatus.NOT_FOUND, "Rider not found")
  val RIDE_NOT_FOUND = Triple(3, HttpStatus.NOT_FOUND, "Ride not found")
  val TRIP_NOT_FOUND = Triple(4, HttpStatus.NOT_FOUND, "Trip not found")
  val DRIVER_ALREADY_EXISTS = Triple(5, HttpStatus.CONFLICT, "Driver with this phone already exists")
  val RIDER_ALREADY_EXISTS = Triple(6, HttpStatus.CONFLICT, "Rider with this phone already exists")
  val INVALID_RIDE_STATUS = Triple(7, HttpStatus.UNPROCESSABLE_ENTITY, "Ride is not in expected status")
  val INVALID_TRIP_STATUS = Triple(8, HttpStatus.UNPROCESSABLE_ENTITY, "Trip is not in expected status")
  val DRIVER_HAS_ACTIVE_RIDE = Triple(9, HttpStatus.CONFLICT, "Driver already has an active ride")
  val RIDE_NOT_ASSIGNED_TO_DRIVER = Triple(10, HttpStatus.FORBIDDEN, "Ride is not assigned to this driver")
  val CONCURRENT_MODIFICATION = Triple(11, HttpStatus.CONFLICT, "Concurrent modification detected")
  val NO_DRIVERS_AVAILABLE = Triple(12, HttpStatus.UNPROCESSABLE_ENTITY, "No drivers available nearby")
  val TRIP_FARE_NOT_CALCULATED = Triple(13, HttpStatus.UNPROCESSABLE_ENTITY, "Trip fare has not been calculated")
  val DUPLICATE_RIDE = Triple(14, HttpStatus.CONFLICT, "Duplicate ride request")
  val DUPLICATE_PAYMENT = Triple(15, HttpStatus.CONFLICT, "Duplicate payment request")
  val INVALID_TYPE_ID = Triple(16, HttpStatus.BAD_REQUEST, "Invalid type ID for the given group")
  val GENERIC_EXCEPTION = Triple(99, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.")
  val DEFAULT_TENANT_NOT_FOUND = Triple(100, HttpStatus.INTERNAL_SERVER_ERROR, "Default tenant not found")
}