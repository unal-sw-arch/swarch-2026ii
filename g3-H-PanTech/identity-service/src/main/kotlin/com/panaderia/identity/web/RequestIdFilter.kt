package com.panaderia.identity.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        request.setAttribute(ATTRIBUTE, requestId)
        response.setHeader(HEADER, requestId)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val ATTRIBUTE = "identity.requestId"

        fun of(request: HttpServletRequest): String =
            request.getAttribute(ATTRIBUTE) as? String
                ?: request.getHeader(HEADER)
                ?: UUID.randomUUID().toString()
    }
}
