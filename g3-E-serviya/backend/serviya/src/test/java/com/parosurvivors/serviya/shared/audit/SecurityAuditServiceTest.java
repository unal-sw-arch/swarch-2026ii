package com.parosurvivors.serviya.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La auditoría enriquece con el contexto de la petición y es de mejor esfuerzo (no rompe el negocio). */
@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

    @Mock private SecurityAuditPort auditPort;

    @AfterEach
    void clear() {
        SecurityAuditContext.clear();
    }

    @Test
    void enrichesWithRequestContext() {
        SecurityAuditContext.set("1.2.3.4", "curl/8", "corr-123");
        new SecurityAuditService(auditPort).record(AuditEventType.LOGIN_SUCCESS, 42L);

        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(auditPort).record(captor.capture());
        SecurityAuditEntry entry = captor.getValue();
        assertThat(entry.eventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(entry.userId()).isEqualTo(42L);
        assertThat(entry.success()).isTrue();
        assertThat(entry.ip()).isEqualTo("1.2.3.4");
        assertThat(entry.userAgent()).isEqualTo("curl/8");
        assertThat(entry.correlationId()).isEqualTo("corr-123");
        assertThat(entry.occurredAt()).isNotNull();
    }

    @Test
    void auditFailureDoesNotPropagate() {
        doThrow(new RuntimeException("db down")).when(auditPort).record(org.mockito.ArgumentMatchers.any());

        // Mejor esfuerzo: un fallo al auditar NO rompe el flujo de negocio.
        assertThatCode(() -> new SecurityAuditService(auditPort)
                .record(AuditEventType.LOGIN_FAILURE, null, false, "x"))
                .doesNotThrowAnyException();
    }
}
