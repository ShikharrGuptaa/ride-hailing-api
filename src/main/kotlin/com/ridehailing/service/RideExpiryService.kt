package com.ridehailing.service

import com.ridehailing.mapper.RideMapper
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.RideStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RideExpiryService(
  private val rideMapper: RideMapper,
  private val driverService: DriverService,
  private val rideEventService: RideEventService
) {

  private val log = LoggerFactory.getLogger(RideExpiryService::class.java)

  @Scheduled(fixedRate = 300000) // every 5 minutes
  @Transactional
  fun expireStaleRides() {
    val staleRides = rideMapper.findStaleRides()
    if (staleRides.isEmpty()) return

    log.info("expireStaleRides - Found ${staleRides.size} stale rides to expire")

    staleRides.forEach { ride ->
      val updated = rideMapper.updateStatus(ride.id!!, RideStatus.CANCELLED.id, ride.status?.id)
      if (updated > 0) {
        log.info("expireStaleRides - Expired ride ${ride.id}")
        // Free up driver if assigned
        if (ride.driverId != null) {
          driverService.updateStatus(ride.driverId, DriverStatus.ONLINE.id)
        }
        val cancelledRide = rideMapper.findById(ride.id)!!
        rideEventService.broadcastRideUpdate(cancelledRide)
      }
    }
  }
}
