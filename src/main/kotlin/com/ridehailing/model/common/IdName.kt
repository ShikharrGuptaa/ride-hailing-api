package com.ridehailing.model.common

data class IdName(
  val id: Int? = null,
  val name: String? = null
)

fun Enum<*>.toIdName(): IdName {
  val enumId = this::class.java.getDeclaredMethod("getId").invoke(this) as Int
  return IdName(enumId, this.name)
}
