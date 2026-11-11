package com.parosurvivors.serviya.shared.audit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste la auditoría en {@code security_audit} y emite la métrica {@code security.audit.event{type,result}}
 * (T6.2: de aquí salen "refresh por minuto" = eventos {@code REFRESH} y "reusos detectados" =
 * {@code REFRESH_REUSE_DETECTED}).
 *
 * <p><b>{@code REQUIRES_NEW}:</b> la auditoría se persiste en su PROPIA transacción, independiente de la del
 * negocio. Así el evento queda registrado aunque la operación de negocio termine lanzando (p. ej. el refresh
 * que detecta reuso revoca la familia y lanza 401): el intento SIEMPRE se audita.</p>
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditPersistenceAdapter implements SecurityAuditPort {

    private static final String METRIC = "security.audit.event";

    private final SecurityAuditRepository repository;
    private final MeterRegistry meters;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SecurityAuditEntry entry) {
        SecurityAuditEntity entity = new SecurityAuditEntity();
        entity.setEventType(entry.eventType());
        entity.setUserId(entry.userId());
        entity.setSuccess(entry.success());
        entity.setDetail(entry.detail());
        entity.setIp(entry.ip());
        entity.setUserAgent(entry.userAgent());
        entity.setCorrelationId(entry.correlationId());
        entity.setOccurredAt(entry.occurredAt());
        repository.save(entity);

        meters.counter(METRIC, "type", entry.eventType().name(),
                "result", entry.success() ? "success" : "failure").increment();
    }
}
