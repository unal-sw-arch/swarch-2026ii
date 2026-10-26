package com.parosurvivors.serviya.feedback.application.ports.input;

import com.parosurvivors.serviya.feedback.application.dto.command.SubmitClientFeedbackCommand;
import com.parosurvivors.serviya.feedback.application.dto.result.ClientFeedbackResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Puerto de entrada de ClientFeedbackService — fachada del feedback del oferente al cliente.
 * Recibe Command; las lecturas devuelven Result (vista agregada rating+resena). Nunca tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 5).
 */
public interface ClientFeedbackServicePort {

    void submitClientFeedback(SubmitClientFeedbackCommand command);

    ClientFeedbackResult getClientFeedback(Long requestId);

    /** True si la solicitud ya tiene feedback de cliente (para habilitar/ocultar "calificar cliente"). */
    boolean existsForRequest(Long requestId);

    /**
     * Lee un feedback de cliente por su id propio (no por requestId). Devuelve {@link Optional#empty()}
     * si no existe — p.ej. si el feedback fue revertido (el revert borra la fila). Lo usa el detalle de
     * reporte de moderación, cuyo link almacena el feedbackId.
     */
    Optional<ClientFeedbackResult> getClientFeedbackById(Long feedbackId);

    Page<ClientFeedbackResult> getClientFeedbackList(Long clientId, Pageable pageable);

    Page<ClientFeedbackResult> getClientFeedbackByOfferer(Long offererId, Pageable pageable);

    /**
     * Revierte (elimina) un feedback de cliente por su id propio y publica el evento de reverso. Devuelve
     * {@code false} si no existe (p.ej. ya revertido). Recibe el feedbackId — no el requestId — para evitar
     * un doble lookup del mismo feedback desde la moderación (cuyo link de reporte guarda el feedbackId).
     */
    boolean revertFeedbackById(Long feedbackId);
}
