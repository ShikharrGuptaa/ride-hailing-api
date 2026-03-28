package com.ridehailing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RideHailingApplication

fun main(args: Array<String>) {
  runApplication<RideHailingApplication>(*args)
}
