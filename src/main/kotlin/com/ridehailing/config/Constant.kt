package com.ridehailing.config

object Constant {

  object Redis {
    const val DRIVER_LOCATION_KEY = "driver:location:"
    const val DRIVER_CACHE_KEY = "driver:cache:"
    const val RIDE_CACHE_KEY = "ride:cache:"
    const val LOCATION_TTL_SECONDS = 60L
    const val RIDE_CACHE_TTL_SECONDS = 300L
  }

  object DriverMatching {
    const val DEFAULT_SEARCH_RADIUS_KM = 5.0
    const val MAX_NEARBY_DRIVERS = 10
  }

  object Tenant {
    const val DEFAULT_CODE = "DEFAULT"
  }

  object Validation {
    const val INDIAN_MOBILE_REGEX = "^[6-9]\\d{9}$"
  }

  object Fare {
    const val ECONOMY_BASE = "30"
    const val ECONOMY_PER_KM = "12"
    const val ECONOMY_PER_MIN = "1.5"
    const val PREMIUM_BASE = "50"
    const val PREMIUM_PER_KM = "18"
    const val PREMIUM_PER_MIN = "2.5"
    const val SUV_BASE = "70"
    const val SUV_PER_KM = "22"
    const val SUV_PER_MIN = "3.0"
    const val AVG_CITY_SPEED_KMH = 25
  }

  object Currency {
    const val INR = "INR"
  }
}
