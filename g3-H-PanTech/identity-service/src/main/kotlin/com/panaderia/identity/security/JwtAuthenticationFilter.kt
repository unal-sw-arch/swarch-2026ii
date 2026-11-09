package com.panaderia.identity.security

import com.panaderia.identity.auth.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Acepta dos fuentes de identidad:
 *  1. El JWT en `Authorization: Bearer ...` (llamadas directas y smoke tests).
 *  2. Los encabezados que agrega el API Gateway después de validar el JWT.
 *
 * En ambos casos el servicio vuelve a construir el principal y las autoridades,
 * de modo que `@PreAuthorize` nunca confía solo en el borde.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val principal = principalFromGatewayHeaders(request) ?: principalFromBearer(request)
            if (principal != null && SecurityContextHolder.getContext().authentication == null) {
                val authorities = buildList {
                    principal.permissions.forEach { add(SimpleGrantedAuthority(it)) }
                    principal.roles.forEach { add(SimpleGrantedAuthority("ROLE_$it")) }
                }
                val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (ex: Exception) {
            log.debug("Token o identidad inválida: {}", ex.message)
            SecurityContextHolder.clearContext()
        }
        filterChain.doFilter(request, response)
    }

    private fun principalFromGatewayHeaders(request: HttpServletRequest): AuthenticatedUser? {
        val userId = request.getHeader("X-User-Id")?.takeIf { it.isNotBlank() } ?: return null
        val id = runCatching { UUID.fromString(userId) }.getOrNull() ?: return null
        return AuthenticatedUser(
            id = id,
            email = request.getHeader("X-User-Email")?.takeIf { it.isNotBlank() },
            roles = csv(request.getHeader("X-Roles")),
            permissions = csv(request.getHeader("X-Permissions")),
            level = request.getHeader("X-Level")?.toIntOrNull() ?: 0,
        )
    }

    private fun principalFromBearer(request: HttpServletRequest): AuthenticatedUser? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        val token = header.substring(7).trim()
        if (token.isEmpty()) return null
        return jwtService.principal(jwtService.validate(token))
    }

    private fun csv(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
