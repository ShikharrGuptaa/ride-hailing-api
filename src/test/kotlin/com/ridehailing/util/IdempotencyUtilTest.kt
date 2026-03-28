package com.ridehailing.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IdempotencyUtilTest {

  @Test
  fun `generateKey - same inputs produce same hash`() {
    val key1 = IdempotencyUtil.generateKey("rider-1", 19.076, 72.877, 19.100, 72.900, 501)
    val key2 = IdempotencyUtil.generateKey("rider-1", 19.076, 72.877, 19.100, 72.900, 501)
    assertEquals(key1, key2)
  }

  @Test
  fun `generateKey - different inputs produce different hash`() {
    val key1 = IdempotencyUtil.generateKey("rider-1", 19.076, 72.877)
    val key2 = IdempotencyUtil.generateKey("rider-2", 19.076, 72.877)
    assertNotEquals(key1, key2)
  }

  @Test
  fun `generateKey - returns 64 char hex string (SHA-256)`() {
    val key = IdempotencyUtil.generateKey("test")
    assertEquals(64, key.length)
    assertTrue(key.matches(Regex("[0-9a-f]+")))
  }

  @Test
  fun `generateKey - handles null fields`() {
    val key = IdempotencyUtil.generateKey("test", null, "value")
    assertNotNull(key)
    assertEquals(64, key.length)
  }
}
