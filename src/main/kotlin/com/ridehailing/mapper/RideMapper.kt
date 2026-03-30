package com.ridehailing.mapper

import com.ridehailing.model.ride.Ride
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.util.UUID

@Mapper
interface RideMapper {

  fun insert(ride: Ride)

  fun findById(@Param("id") id: UUID): Ride?

  fun findByIdempotencyKey(@Param("idempotencyKey") idempotencyKey: String): Ride?

  fun updateStatus(
    @Param("id") id: UUID,
    @Param("statusId") statusId: Int,
    @Param("expectedStatusId") expectedStatusId: Int? = null
  ): Int

  fun assignDriver(
    @Param("id") id: UUID,
    @Param("driverId") driverId: UUID,
    @Param("statusId") statusId: Int
  ): Int

  fun findActiveByDriverId(@Param("driverId") driverId: UUID): Ride?

  fun findAvailableByVehicleType(@Param("vehicleTypeId") vehicleTypeId: Int): List<Ride>

  fun findStaleRides(): List<Ride>
}
