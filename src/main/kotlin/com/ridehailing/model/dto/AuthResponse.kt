package com.ridehailing.model.dto

data class AuthResponse<T>(
  val token: String,
  val user: T
)
