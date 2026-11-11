package com.parosurvivors.notifications.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Expone el {@code X-Request-Id} de la petición en el MDC (clave {@code requestId}) para que todas las líneas
 * de log lo lleven (T6.4) y se pueda correlacionar de punta a punta con el gateway y el monolito. El patrón
 * de log lo inyecta vía {@code %X{requestId}} (ver {@code logging.pattern} en application.yaml). Limpia el MDC
 * al salir para no filtrar el id al reutilizar el hilo del pool.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdMdcFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader("X-Request-Id");
        try {
            MDC.put(MDC_KEY, correlationId != null ? correlationId : "-");
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
