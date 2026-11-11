package com.parosurvivors.serviya.shared.audit;

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
 * Captura IP, user agent y {@code X-Request-Id} de cada petición en {@link SecurityAuditContext} para que la
 * auditoría pueda enriquecer sus eventos sin acoplar las capas de negocio al {@code HttpServletRequest}.
 * La IP real llega por {@code X-Forwarded-For} detrás del gateway ({@code forward-headers-strategy=native}),
 * así que {@code getRemoteAddr()} ya la resuelve. Limpia el contexto al salir (evita fugas entre peticiones
 * reutilizando el hilo del pool).
 *
 * <p>También expone el {@code X-Request-Id} en el MDC (clave {@code requestId}) para que TODAS las líneas de
 * log de la petición lo lleven (T6.4) y se puedan correlacionar de punta a punta con el gateway. El patrón de
 * log lo inyecta vía {@code %X{requestId}} (ver {@code logging.pattern} en application.yaml).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditContextFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader("X-Request-Id");
        try {
            SecurityAuditContext.set(request.getRemoteAddr(), request.getHeader("User-Agent"), correlationId);
            MDC.put(MDC_KEY, correlationId != null ? correlationId : "-");
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            SecurityAuditContext.clear();
        }
    }
}
