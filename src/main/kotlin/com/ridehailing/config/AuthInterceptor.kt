package com.ridehailing.config

import com.ridehailing.service.JwtService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthInterceptor(
  private val jwtService: JwtService
) : HandlerInterceptor {

  private val log = LoggerFactory.getLogger(AuthInterceptor::class.java)

  companion object {
    val PUBLIC_PATHS = listOf(
      "/riders", "/drivers", // POST registration (checked by method)
      "/actuator", "/swagger", "/api-docs", "/v3/api-docs"
    )
  }

  override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
    // Allow OPTIONS (CORS preflight)
    if (request.method == "OPTIONS") return true

    val path = request.requestURI.removePrefix("/v1")

    // Allow public endpoints
    if (isPublicEndpoint(path, request.method)) return true

    val authHeader = request.getHeader("Authorization")
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.warn("preHandle - Missing or invalid Authorization header for $path")
      response.status = 401
      response.contentType = "application/json"
      response.writer.write("""{"success":false,"error":{"code":25,"message":"Unauthorized"}}""")
      return false
    }

    val token = authHeader.substring(7)
    val claims = jwtService.validateToken(token)
    if (claims == null) {
      log.warn("preHandle - Invalid token for $path")
      response.status = 401
      response.contentType = "application/json"
      response.writer.write("""{"success":false,"error":{"code":25,"message":"Invalid or expired token"}}""")
      return false
    }

    // Store user info in request attributes for controllers
    request.setAttribute("userId", jwtService.getUserId(claims))
    request.setAttribute("userRole", jwtService.getRole(claims))

    return true
  }

  private fun isPublicEndpoint(path: String, method: String): Boolean {
    // Registration endpoints (POST only)
    if (method == "POST" && (path == "/riders" || path == "/drivers")) return true
    // Swagger/actuator
    if (PUBLIC_PATHS.any { path.startsWith(it) && it.length > 2 }) return true
    // Available rides (drivers poll this)
    if (path.startsWith("/rides/available")) return true
    return false
  }
}