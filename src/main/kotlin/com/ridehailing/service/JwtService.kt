package com.ridehailing.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
  @Value("\${jwt.secret}") private val secret: String,
  @Value("\${jwt.expiration-hours}") private val expirationHours: Long
) {

  private val log = LoggerFactory.getLogger(JwtService::class.java)

  private val key: SecretKey by lazy {
    Keys.hmacShaKeyFor(secret.toByteArray())
  }

  fun generateToken(userId: UUID, role: String): String {
    log.info("generateToken - Generating token for user: $userId, role: $role")
    val now = Date()
    val expiry = Date(now.time + expirationHours * 3600 * 1000)

    return Jwts.builder()
      .subject(userId.toString())
      .claim("role", role)
      .issuedAt(now)
      .expiration(expiry)
      .signWith(key)
      .compact()
  }

  fun validateToken(token: String): Claims? {
    return try {
      Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload
    } catch (e: Exception) {
      log.warn("validateToken - Invalid token: ${e.message}")
      null
    }
  }

  fun getUserId(claims: Claims): UUID = UUID.fromString(claims.subject)

  fun getRole(claims: Claims): String = claims["role"] as String
}
