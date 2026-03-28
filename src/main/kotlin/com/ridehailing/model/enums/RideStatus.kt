package com.ridehailing.model.enums

enum class RideStatus(val id: Int) {
  REQUESTED(201), MATCHING(202), DRIVER_ASSIGNED(203),
  DRIVER_ACCEPTED(204), IN_PROGRESS(205), COMPLETED(206), CANCELLED(207)
}
