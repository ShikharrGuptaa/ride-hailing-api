package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.mapper.RiderMapper
import com.ridehailing.model.ApplicationException
import com.ridehailing.model.Rider
import com.ridehailing.model.dto.CreateRiderRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RiderService(
  private val riderMapper: RiderMapper,
  private val tenantService: TenantService
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  fun createRider(request: CreateRiderRequest): Rider {
    log.info("createRider - Creating rider: ${request.name}, phone: ${request.phone}")

    val tenantId = tenantService.getDefaultTenantId()

    val existing = riderMapper.findByPhoneAndTenant(request.phone, tenantId)
    if (existing != null) {
      throw ApplicationException(ApplicationExceptionTypes.RIDER_ALREADY_EXISTS)
    }

    val rider = Rider(
      tenantId = tenantId,
      regionId = request.regionId,
      name = request.name,
      phone = request.phone,
      email = request.email
    )

    riderMapper.insert(rider)
    log.info("createRider - Rider created")
    return riderMapper.findByPhoneAndTenant(rider.phone, tenantId)!!
  }

  fun findById(riderId: UUID): Rider {
    log.debug("findById - Looking up rider: $riderId")
    return riderMapper.findById(riderId)
      ?: throw ApplicationException(ApplicationExceptionTypes.RIDER_NOT_FOUND)
  }
}
