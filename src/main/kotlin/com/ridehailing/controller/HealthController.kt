package com.ridehailing.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

  @GetMapping("/health")
  fun health(): Map<String, String> {
    return mapOf("status" to "UP")
  }

  @GetMapping("/ping")
  fun ping(): Map<String, String> {
    return mapOf("message" to "pong")
  }
}
