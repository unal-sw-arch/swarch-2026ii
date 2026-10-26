package com.parosurvivors.serviya.requests.domain;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Propuesta de reprogramación de una solicitud. Mapea la tabla {@code reschedule_proposals}.
 * Sólo puede resolverse (aceptar/rechazar) mientras esté {@code PENDING}; las transiciones
 * son idempotentes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleProposal {
    private Long id;
    private Long requestId;
    /** Denormalizados desde la solicitud (inmutables): la parte cliente y oferente de la propuesta. */
    private Long clientId;
    private Long offererId;
    private String reason;
    private LocalDateTime proposedDate;
    private ProposalStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean isPending() {
        return status == ProposalStatus.PENDING;
    }

    /** Cualquier estado distinto de {@code PENDING} es resuelto (terminal). */
    public boolean isResolved() {
        return status != ProposalStatus.PENDING;
    }

    /** El cliente acepta la fecha propuesta. */
    public void accept() {
        resolveTo(ProposalStatus.ACCEPTED, "aceptar");
    }

    /** El cliente rechaza la propuesta sin reprogramar; la solicitud conserva su fecha original. */
    public void reject() {
        resolveTo(ProposalStatus.REJECTED, "rechazar");
    }

    /** El oferente retira su propia propuesta (≠ que el cliente la rechace). */
    public void cancel() {
        resolveTo(ProposalStatus.CANCELLED, "cancelar");
    }

    /**
     * El cliente reprogramó libremente en vez de responder esta propuesta, por lo que queda superada.
     * Aplica sea cual sea el origen de la reprogramación libre (viendo la propuesta o desde la solicitud).
     */
    public void markSuperseded() {
        resolveTo(ProposalStatus.SUPERSEDED, "superar");
    }

    /** Transición idempotente a un estado resuelto: repetir el estado ya aplicado no falla. */
    private void resolveTo(ProposalStatus target, String action) {
        if (status == target) {
            return;
        }
        requirePending(action);
        this.status = target;
        this.respondedAt = LocalDateTime.now();
    }

    private void requirePending(String action) {
        if (!isPending()) {
            throw new InvalidStateException(
                    "No se puede " + action + " una propuesta en estado " + status);
        }
    }
}
