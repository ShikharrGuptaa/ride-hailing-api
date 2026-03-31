package com.ridehailing.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class RideAutoAssignService(
  private val rideService: RideService
) {

  private val log = LoggerFactory.getLogger(RideAutoAssignService::class.java)

  @Scheduled(fixedRate = 15000) // every 15 seconds
  fun autoAssignUnacceptedRides() {
    val unaccepted = rideService.findUnacceptedRides()
    if (unaccepted.isEmpty()) return

    log.info("autoAssignUnacceptedRides - Found ${unaccepted.size} rides pending auto-assignment")

    unaccepted.forEach { ride ->
      try {
        val assigned = rideService.autoAssignDriver(ride)
        if (!assigned) {
          log.info("autoAssignUnacceptedRides - No driver available for ride ${ride.id}, will retry next cycle")
        }
      } catch (e: Exception) {
        log.error("autoAssignUnacceptedRides - Error auto-assigning ride ${ride.id}: ${e.message}")
      }
    }
  }
}
