package com.ridehailing.mapper

import com.ridehailing.model.driver.Driver
import com.ridehailing.model.driver.DriverCurrentLocation
import com.ridehailing.model.driver.DriverLocation
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.util.UUID

@Mapper
interface DriverMapper {

  fun insert(driver: Driver)

  fun findById(@Param("id") id: UUID): Driver?

  fun findByPhoneAndTenant(@Param("phone") phone: String, @Param("tenantId") tenantId: UUID): Driver?

  fun updateStatus(@Param("id") id: UUID, @Param("statusId") statusId: Int): Int

  fun updateVehicleType(@Param("id") id: UUID, @Param("vehicleTypeId") vehicleTypeId: Int, @Param("licensePlate") licensePlate: String?): Int

  fun upsertCurrentLocation(location: DriverCurrentLocation)

  fun findCurrentLocation(@Param("driverId") driverId: UUID): DriverCurrentLocation?

  fun findNearbyAvailable(
    @Param("lat") lat: Double,
    @Param("lng") lng: Double,
    @Param("vehicleTypeId") vehicleTypeId: Int,
    @Param("radiusKm") radiusKm: Double,
    @Param("limit") limit: Int
  ): List<Driver>

  fun insertLocation(location: DriverLocation)
}
