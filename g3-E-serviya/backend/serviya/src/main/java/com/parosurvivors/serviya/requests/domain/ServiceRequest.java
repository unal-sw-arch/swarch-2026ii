package com.parosurvivors.serviya.requests.domain;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Solicitud de servicio: tabla central del sistema con su máquina de estados.
 * Mapea la tabla {@code service_requests}.
 *
 * <p>Las transiciones validan el estado actual (lanzan {@link InvalidStateException} → HTTP 409)
 * y son idempotentes: repetir una transición ya aplicada no falla. La reprogramación nunca
 * edita esta solicitud; crea una nueva enlazada por {@code previousRequestId} y marca ésta
 * como {@code RESCHEDULED} (ver CLAUDE.md, "State machine").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ServiceRequest {
    private Long id;
    private Long serviceId;
    private Long previousRequestId;
    private Long clientId;
    private Long offererId;
    private Long addressId;
    private LocalDateTime scheduledDate;
    private RequestStatus status;
    private Long updatedBy;
    private BigDecimal requestedPrice;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedStatusAt;

    // =====================================================
    // STATE QUERIES
    // =====================================================

    public boolean isPending() {
        return status == RequestStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == RequestStatus.ACCEPTED;
    }

    public boolean isCompleted() {
        return status == RequestStatus.COMPLETED;
    }

    /** El oferente la marcó como prestada; espera la confirmación del cliente. */
    public boolean isPresumablyCompleted() {
        return status == RequestStatus.PRESUMABLY_COMPLETED;
    }

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    // =====================================================
    // STATE TRANSITIONS (idempotentes)
    // =====================================================

    public void accept(Long offererId) {
        if (isAccepted()) {
            return;
        }
        requireStatus(RequestStatus.PENDING, "aceptar");
        transitionTo(RequestStatus.ACCEPTED, offererId);
    }

    public void reject(Long offererId) {
        if (status == RequestStatus.REJECTED) {
            return;
        }
        requireStatus(RequestStatus.PENDING, "rechazar");
        transitionTo(RequestStatus.REJECTED, offererId);
    }

    public void cancel(Long userId) {
        if (status == RequestStatus.CANCELLED) {
            return;
        }
        if (!isPending() && !isAccepted()) {
            throw new InvalidStateException(
                    "No se puede cancelar una solicitud en estado " + status);
        }
        transitionTo(RequestStatus.CANCELLED, userId);
    }

    /** El oferente declara el servicio como prestado; queda pendiente la confirmación del cliente. */
    public void markAsPresumablyCompleted(Long offererId) {
        if (isPresumablyCompleted() || isCompleted()) {
            return;
        }
        requireStatus(RequestStatus.ACCEPTED, "marcar como prestada");
        this.completedAt = LocalDateTime.now();
        transitionTo(RequestStatus.PRESUMABLY_COMPLETED, offererId);
    }

    /** El cliente confirma que el servicio fue prestado. Sólo desde {@code PRESUMABLY_COMPLETED}. */
    public void confirmCompletion(Long clientId) {
        if (isCompleted()) {
            return;
        }
        requireStatus(RequestStatus.PRESUMABLY_COMPLETED, "confirmar");
        transitionTo(RequestStatus.COMPLETED, clientId);
    }

    /**
     * Marca la solicitud como no prestada. Origen {@code ACCEPTED} (pasó la fecha sin prestarse)
     * o {@code PRESUMABLY_COMPLETED} (disputa: el cliente la reportó y un admin decide). El control
     * de acceso (admin/sistema) es responsabilidad de la capa de seguridad, no del dominio.
     */
    public void markAsNotProvided(Long userId) {
        if (status == RequestStatus.NOT_PROVIDED) {
            return;
        }
        if (!isAccepted() && !isPresumablyCompleted()) {
            throw new InvalidStateException(
                    "Sólo una solicitud aceptada o presuntamente prestada puede marcarse como no prestada; estado actual: " + status);
        }
        transitionTo(RequestStatus.NOT_PROVIDED, userId);
    }

    /** Marca esta solicitud como reprogramada (su reemplazo se crea aparte enlazado por previousRequestId). */
    public void markRescheduled(Long userId) {
        if (status == RequestStatus.RESCHEDULED) {
            return;
        }
        if (!isPending() && !isAccepted()) {
            throw new InvalidStateException(
                    "No se puede reprogramar una solicitud en estado " + status);
        }
        transitionTo(RequestStatus.RESCHEDULED, userId);
    }

    /**
     * Crea la solicitud de reemplazo al reprogramar (patrón Prototype vía {@code toBuilder}):
     * copia todos los campos de ésta, la enlaza por {@code previousRequestId} y fija la nueva
     * fecha y estado ({@code ACCEPTED} si viene de aceptar una propuesta, {@code PENDING} si es
     * reprogramación libre). No modifica esta solicitud (llamar antes {@link #markRescheduled}).
     */
    public ServiceRequest rescheduleTo(LocalDateTime newDate, RequestStatus newStatus, Long actorId) {
        return this.toBuilder()
                .id(null)
                .previousRequestId(this.id)
                .scheduledDate(newDate)
                .status(newStatus)
                .completedAt(null)
                .updatedBy(actorId)
                .createdAt(LocalDateTime.now())
                .updatedStatusAt(LocalDateTime.now())
                .build();
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void requireStatus(RequestStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStateException(
                    "No se puede " + action + " una solicitud en estado " + status
                            + " (se esperaba " + expected + ")");
        }
    }

    private void transitionTo(RequestStatus newStatus, Long userId) {
        this.status = newStatus;
        this.updatedBy = userId;
        this.updatedStatusAt = LocalDateTime.now();
    }
}
