package com.parosurvivors.serviya.shared.audit;

/** Puerto de salida de la auditoría de seguridad: persiste una entrada (el adaptador vive en infraestructura). */
public interface SecurityAuditPort {
    void record(SecurityAuditEntry entry);
}
