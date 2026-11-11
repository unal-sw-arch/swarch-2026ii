package com.parosurvivors.serviya.shared.audit;

/**
 * Tipos de evento de la auditoría de seguridad (Fase 6, T6.1). Se registran en {@code security_audit} con
 * userId, IP, user agent, correlationId, resultado y fecha. El {@code REFRESH_REUSE_DETECTED} es la señal de
 * robo de refresh (revoca la familia); dispara además una alerta (T6.3).
 */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    REFRESH,
    REFRESH_REUSE_DETECTED,
    LOGOUT,
    LOGOUT_ALL,
    BAN,
    ROLE_REVOKE,
    PASSWORD_CHANGE,
    PASSWORD_RESET,
    ACCOUNT_DELETE
}
