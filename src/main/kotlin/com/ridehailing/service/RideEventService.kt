package com.ridehailing.service

import com.ridehailing.model.ride.Ride
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class RideEventService(
  private val messagingTemplate: SimpMessagingTemplate
) {

  private val log = LoggerFactory.getLogger(RideEventService::class.java)

  fun broadcastNewRide(ride: Ride) {
    log.info("broadcastNewRide - Broadcasting new ride: ${ride.id}")
    messagingTemplate.convertAndSend("/topic/rides/available", ride)
  }

  fun broadcastRideUpdate(ride: Ride) {
    log.info("broadcastRideUpdate - Broadcasting ride update: ${ride.id}, status: ${ride.status?.id}")
    messagingTemplate.convertAndSend("/topic/rides/${ride.id}", ride)
    // Also notify the driver if assigned
    if (ride.driverId != null) {
      messagingTemplate.convertAndSend("/topic/drivers/${ride.driverId}/rides", ride)
    }
  }
}
