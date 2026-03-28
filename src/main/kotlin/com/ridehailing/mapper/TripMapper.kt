package com.ridehailing.mapper

import com.ridehailing.model.trip.Trip
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.math.BigDecimal
import java.util.UUID

@Mapper
interface TripMapper {

  fun insert(trip: Trip)

  fun findById(@Param("id") id: UUID): Trip?

  fun findByRideId(@Param("rideId") rideId: UUID): Trip?

  fun endTrip(
    @Param("id") id: UUID,
    @Param("endLat") endLat: Double,
    @Param("endLng") endLng: Double,
    @Param("distanceKm") distanceKm: BigDecimal,
    @Param("durationMinutes") durationMinutes: BigDecimal,
    @Param("baseFare") baseFare: BigDecimal,
    @Param("distanceFare") distanceFare: BigDecimal,
    @Param("timeFare") timeFare: BigDecimal,
    @Param("totalFare") totalFare: BigDecimal
  ): Int

  fun updateStatus(
    @Param("id") id: UUID,
    @Param("statusId") statusId: Int
  ): Int

  fun getDriverEarnings(@Param("driverId") driverId: UUID): Map<String, Any>?
}
