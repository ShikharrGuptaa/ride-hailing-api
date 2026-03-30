package com.ridehailing.service

import com.ridehailing.config.ApplicationExceptionTypes
import com.ridehailing.mapper.DriverMapper
import com.ridehailing.model.common.ApplicationException
import com.ridehailing.model.common.IdName
import com.ridehailing.model.driver.Driver
import com.ridehailing.model.dto.CreateDriverRequest
import com.ridehailing.model.enums.DriverStatus
import com.ridehailing.model.enums.VehicleType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DriverServiceTest {

  @Mock lateinit var driverMapper: DriverMapper
  @Mock lateinit var tenantService: TenantService
  @Mock lateinit var redisTemplate: RedisTemplate<String, Any>
  @Mock lateinit var valueOps: ValueOperations<String, Any>

  @InjectMocks lateinit var driverService: DriverService

  private val tenantId = UUID.randomUUID()

  @Test
  fun `createDriver - success`() {
    val request = CreateDriverRequest(
      name = "Test Driver", phone = "9876543210",
      vehicleTypeId = VehicleType.ECONOMY.id, licensePlate = "MH01AB1234"
    )

    whenever(tenantService.getDefaultTenantId()).thenReturn(tenantId)
    whenever(driverMapper.findByPhoneAndTenant("9876543210", tenantId)).thenReturn(null)
    doNothing().whenever(driverMapper).insert(any())

    val expectedDriver = Driver(
      id = UUID.randomUUID(), tenantId = tenantId, name = "Test Driver",
      phone = "9876543210", vehicleType = IdName(VehicleType.ECONOMY.id),
      licensePlate = "MH01AB1234", status = IdName(DriverStatus.OFFLINE.id)
    )
    whenever(driverMapper.findByPhoneAndTenant("9876543210", tenantId)).thenReturn(null, expectedDriver)

    val result = driverService.createDriver(request)
    assertNotNull(result)
    assertEquals("Test Driver", result.name)
    verify(driverMapper).insert(any())
  }

  @Test
  fun `createDriver - duplicate phone returns existing driver`() {
    val request = CreateDriverRequest(
      name = "Test Driver", phone = "9876543210",
      vehicleTypeId = VehicleType.ECONOMY.id, licensePlate = "MH01AB1234"
    )

    val existingDriver = Driver(
      id = UUID.randomUUID(), tenantId = tenantId, name = "Existing",
      phone = "9876543210", vehicleType = IdName(VehicleType.ECONOMY.id),
      licensePlate = "MH01XX0000"
    )

    whenever(tenantService.getDefaultTenantId()).thenReturn(tenantId)
    whenever(driverMapper.findByPhoneAndTenant("9876543210", tenantId)).thenReturn(existingDriver)

    val result = driverService.createDriver(request)
    assertEquals(existingDriver.id, result.id)
    assertEquals("Existing", result.name)
    verify(driverMapper, never()).insert(any())
  }

  @Test
  fun `createDriver - invalid vehicleTypeId throws exception`() {
    val request = CreateDriverRequest(
      name = "Test", phone = "9876543210",
      vehicleTypeId = 999, licensePlate = "MH01AB1234"
    )

    val ex = assertThrows<ApplicationException> { driverService.createDriver(request) }
    assertEquals(ApplicationExceptionTypes.INVALID_TYPE_ID.first, ex.code)
  }

  @Test
  fun `findById - not found throws exception`() {
    val id = UUID.randomUUID()
    whenever(driverMapper.findById(id)).thenReturn(null)

    val ex = assertThrows<ApplicationException> { driverService.findById(id) }
    assertEquals(ApplicationExceptionTypes.DRIVER_NOT_FOUND.first, ex.code)
  }

  @Test
  fun `updateLocation - driver not found throws exception`() {
    val id = UUID.randomUUID()
    whenever(driverMapper.findById(id)).thenReturn(null)

    val ex = assertThrows<ApplicationException> { driverService.updateLocation(id, 19.0, 72.0) }
    assertEquals(ApplicationExceptionTypes.DRIVER_NOT_FOUND.first, ex.code)
  }
}
