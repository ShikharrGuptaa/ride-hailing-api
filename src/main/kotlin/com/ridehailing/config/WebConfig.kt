package com.ridehailing.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
  private val authInterceptor: AuthInterceptor
) : WebMvcConfigurer {

  override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(authInterceptor)
      .addPathPatterns("/**")
      .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**")
  }
}
