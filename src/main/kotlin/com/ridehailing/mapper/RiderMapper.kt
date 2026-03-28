package com.ridehailing.mapper

import com.ridehailing.model.rider.Rider
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.util.UUID

@Mapper
interface RiderMapper {

  fun insert(rider: Rider)

  fun findById(@Param("id") id: UUID): Rider?

  fun findByPhoneAndTenant(@Param("phone") phone: String, @Param("tenantId") tenantId: UUID): Rider?
}
