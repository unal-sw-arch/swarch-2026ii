package com.parosurvivors.serviya.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de la tabla {@code security_audit} (T6.1). El esquema lo crea {@code ddl-auto: update}. */
@Entity
@Table(name = "security_audit", indexes = {
        @Index(name = "idx_security_audit_user", columnList = "user_id"),
        @Index(name = "idx_security_audit_type_time", columnList = "event_type, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class SecurityAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 500)
    private String detail;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
