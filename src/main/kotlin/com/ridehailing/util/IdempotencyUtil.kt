package com.ridehailing.util

import java.security.MessageDigest

object IdempotencyUtil {

  fun generateKey(vararg fields: Any?): String {
    val raw = fields.joinToString("|") { it?.toString() ?: "" }
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(raw.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
  }
}
