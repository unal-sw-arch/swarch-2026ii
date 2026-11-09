package com.panaderia.identity.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.panaderia.identity.security.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.util.UUID

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/health",
                    "/ready",
                    "/.well-known/jwks.json",
                    "/auth/register",
                    "/auth/login",
                    "/auth/refresh",
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint())
                it.accessDeniedHandler(accessDeniedHandler())
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(10)

    @Bean
    fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager =
        configuration.authenticationManager

    private fun authenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, _ ->
            writeError(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Falta la autenticación o el token está vencido",
            )
        }

    private fun accessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { request, response, _: AccessDeniedException ->
            writeError(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "El rol o permiso no alcanza para esta operación",
            )
        }

    private fun writeError(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        val requestId = request.getHeader("X-Request-Id")?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        objectMapper.writeValue(
            response.writer,
            mapOf(
                "code" to code,
                "message" to message,
                "request_id" to requestId,
                "details" to emptyMap<String, Any>(),
            ),
        )
    }
}
