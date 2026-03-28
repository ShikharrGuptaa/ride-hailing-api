package com.ridehailing.model.common

data class IdName(
  var id: Int? = null,
  var name: String? = null
) {
  constructor(id: Int) : this(id, null)
}

fun Enum<*>.toIdName(): IdName {
  val enumId = this::class.java.getDeclaredMethod("getId").invoke(this) as Int
  return IdName(enumId, this.name)
}
