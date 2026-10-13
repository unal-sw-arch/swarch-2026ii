package com.parosurvivors.serviya.requests.domain;

/**
 * Estados de una propuesta de reprogramación. Coincide con el ENUM de la columna
 * {@code reschedule_proposals.status}.
 *
 * <ul>
 *   <li>{@code PENDING}   — a la espera de la respuesta del cliente.</li>
 *   <li>{@code ACCEPTED}  — el cliente acepta la fecha propuesta (la solicitud se reprograma).</li>
 *   <li>{@code REJECTED}  — el cliente rechaza sin reprogramar (la solicitud sigue con su fecha original).</li>
 *   <li>{@code CANCELLED} — el oferente retira su propuesta (≠ que el cliente la rechace).</li>
 *   <li>{@code SUPERSEDED}— el cliente reprogramó libremente en vez de responder; la propuesta queda superada.</li>
 * </ul>
 */
public enum ProposalStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    SUPERSEDED
}
