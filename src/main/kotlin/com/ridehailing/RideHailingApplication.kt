package com.ridehailing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class RideHailingApplication

fun main(args: Array<String>) {
  runApplication<RideHailingApplication>(*args)
}
