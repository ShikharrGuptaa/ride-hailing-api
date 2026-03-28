package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.config.Constant
import com.ridehailing.mapper.TenantMapper
import com.ridehailing.model.common.ApplicationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TenantService(
  private val tenantMapper: TenantMapper
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  fun getDefaultTenantId(): UUID {
    log.debug("getDefaultTenantId - Resolving default tenant")
    return tenantMapper.findIdByCode(Constant.Tenant.DEFAULT_CODE)
      ?: throw ApplicationException(ApplicationExceptionTypes.DEFAULT_TENANT_NOT_FOUND)
  }
}
