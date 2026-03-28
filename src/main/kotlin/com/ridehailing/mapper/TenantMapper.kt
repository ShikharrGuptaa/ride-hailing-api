package com.ridehailing.mapper

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.util.UUID

@Mapper
interface TenantMapper {

  fun findIdByCode(@Param("code") code: String): UUID?
}
