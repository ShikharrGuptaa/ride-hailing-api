package com.ridehailing.model

enum class DriverStatus(val id: Int) {
  ONLINE(101), OFFLINE(102), ON_TRIP(103)
}

enum class RideStatus(val id: Int) {
  REQUESTED(201), MATCHING(202), DRIVER_ASSIGNED(203),
  DRIVER_ACCEPTED(204), IN_PROGRESS(205), COMPLETED(206), CANCELLED(207)
}

enum class TripStatus(val id: Int) {
  IN_PROGRESS(301), PAUSED(302), COMPLETED(303)
}

enum class PaymentStatus(val id: Int) {
  PENDING(401), PROCESSING(402), COMPLETED(403), FAILED(404), REFUNDED(405)
}

enum class VehicleType(val id: Int) {
  ECONOMY(501), PREMIUM(502), SUV(503)
}

enum class PaymentMethod(val id: Int) {
  UPI(601), CARD(602), WALLET(603), CASH(604)
}

enum class Region(val id: Int) {
  MUMBAI(701), DELHI(702), BANGALORE(703), HYDERABAD(704), CHENNAI(705)
}