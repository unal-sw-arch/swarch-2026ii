package com.parosurvivors.serviya.requests.domain;

/**
 * Estados de una solicitud de servicio. Coincide con el ENUM de la columna
 * {@code service_requests.status}.
 *
 * <ul>
 *   <li>{@code PENDING}              — creada por el cliente; espera que el oferente la acepte o rechace.</li>
 *   <li>{@code ACCEPTED}             — el oferente la aceptó; el servicio está agendado y aún no se presta.</li>
 *   <li>{@code PRESUMABLY_COMPLETED} — el oferente la declaró prestada; espera la confirmación del cliente
 *       (no terminal). Dispara el feedback de ambas partes y habilita la ruta de disputa.</li>
 *   <li>{@code REJECTED}             — el oferente rechazó la solicitud pendiente. Terminal.</li>
 *   <li>{@code CANCELLED}            — cancelada por el cliente o el oferente antes de completarse. Terminal.</li>
 *   <li>{@code COMPLETED}            — el cliente confirmó que el servicio fue prestado. Terminal.</li>
 *   <li>{@code RESCHEDULED}          — reprogramada; su reemplazo se crea aparte, enlazado por
 *       {@code previousRequestId}. Terminal.</li>
 *   <li>{@code NOT_PROVIDED}         — el servicio no se prestó (no-show con fecha vencida, o disputa
 *       resuelta por un admin). Terminal.</li>
 * </ul>
 */
public enum RequestStatus {
    PENDING,
    ACCEPTED,
    PRESUMABLY_COMPLETED,
    REJECTED,
    CANCELLED,
    COMPLETED,
    RESCHEDULED,
    NOT_PROVIDED;

    /** Estados terminales: ya no admiten más transiciones. */
    public boolean isTerminal() {
        return this == REJECTED
                || this == CANCELLED
                || this == COMPLETED
                || this == RESCHEDULED
                || this == NOT_PROVIDED;
    }
}
