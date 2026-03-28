package com.ridehailing.config

import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import org.apache.ibatis.type.MappedJdbcTypes
import org.apache.ibatis.type.MappedTypes

@MappedTypes(UUID::class)
@MappedJdbcTypes(JdbcType.VARCHAR, JdbcType.CHAR, JdbcType.OTHER)
class UuidTypeHandler : BaseTypeHandler<UUID?>() {

  override fun setNonNullParameter(
    ps: PreparedStatement,
    i: Int,
    parameter: UUID?,
    jdbcType: JdbcType?
  ) {
    ps.setObject(i, parameter)
  }

  @Throws(SQLException::class)
  override fun getNullableResult(rs: ResultSet, columnName: String?): UUID? {
    val value = rs.getObject(columnName) ?: return null
    return if (value is UUID) value else UUID.fromString(value.toString())
  }

  @Throws(SQLException::class)
  override fun getNullableResult(rs: ResultSet, columnIndex: Int): UUID? {
    val value = rs.getObject(columnIndex) ?: return null
    return if (value is UUID) value else UUID.fromString(value.toString())
  }

  @Throws(SQLException::class)
  override fun getNullableResult(cs: CallableStatement, columnIndex: Int): UUID? {
    val value = cs.getObject(columnIndex) ?: return null
    return if (value is UUID) value else UUID.fromString(value.toString())
  }
}
