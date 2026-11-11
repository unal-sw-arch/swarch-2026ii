package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.admin.application.dto.command.RemoveFeedbackCommand;
import com.parosurvivors.serviya.admin.application.ports.input.ModerationServicePort;
import com.parosurvivors.serviya.feedback.api.FeedbackCommandApi;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.reports.api.ReportCommandApi;
import com.parosurvivors.serviya.reports.api.ReportQueryApi;
import com.parosurvivors.serviya.reports.api.dto.ReportSummarySnapshot;
import com.parosurvivors.serviya.requests.api.RequestCommandApi;
import com.parosurvivors.serviya.shared.contract.ReportActions;
import com.parosurvivors.serviya.shared.contract.ReportTypes;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.api.UserCommandApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestador de moderación (rol ADMIN, módulo 9). Cada método ejecuta su acción de dominio (advertir/
 * banear/revertir feedback/marcar no prestada) y luego DELEGA la finalización del reporte en
 * {@link ReportCommandApi#resolveReport}/{@link ReportCommandApi#closeReport}, que se encargan de la
 * transición de estado + registrar la acción + notificar al reporter. Las notificaciones al usuario
 * reportado (advertido/suspendido) sí las emite este servicio, pues son la acción en sí.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 9).
 */
@Component
@RequiredArgsConstructor
public class ModerationService implements ModerationServicePort {

    private final ReportQueryApi reportQueryApi;
    private final ReportCommandApi reportCommandApi;
    private final FeedbackCommandApi feedbackCommandApi;
    private final RequestCommandApi requestCommandApi;
    private final UserCommandApi userCommandApi;
    private final NotificationCommandApi notificationCommandApi;
    /** B5: anti auto-accion y guard de ultimo admin activo. */
    private final AdminActionGuard adminActionGuard;

    @Override
    @Transactional
    public void warnUser(Long reportId, Long adminId) {
        ReportSummarySnapshot report = requireSummary(reportId);
        // La advertencia es la propia notificación al reportado; no hay cambio de estado sobre la cuenta.
        notificationCommandApi.notify(new NotificationCommand(
                report.reportedUserId(),
                "USER_WARNED",
                "Has recibido una advertencia",
                "Un administrador revisó un reporte en tu contra (categoría \"" + report.category()
                        + "\") y te ha advertido. Reincidir puede llevar a la suspensión de tu cuenta.",
                "REPORT",
                reportId,
                null,
                null));
        reportCommandApi.resolveReport(reportId, adminId, ReportActions.WARN);
    }

    @Override
    @Transactional
    public void banUserFromReport(Long reportId, Long adminId, String reason) {
        ReportSummarySnapshot report = requireSummary(reportId);
        // B5: el admin no puede banearse a si mismo ni dejar al sistema sin administradores activos.
        adminActionGuard.requireNotSelf(adminId, report.reportedUserId());
        adminActionGuard.requireAnotherActiveAdmin(report.reportedUserId());
        // Motivo del admin si lo escribió; si no, se deriva de la categoría del reporte (nunca el texto
        // libre del reportante, que es la acusación y podría venir manipulada u ofensiva).
        String finalReason = (reason != null && !reason.isBlank())
                ? reason
                : "reporte en tu contra (categoría \"" + report.category() + "\")";
        userCommandApi.banUser(report.reportedUserId(), finalReason);
        reportCommandApi.resolveReport(reportId, adminId, ReportActions.BAN);
    }

    @Override
    @Transactional
    public void revertFeedbackFromReport(Long reportId, Long adminId) {
        ReportSummarySnapshot report = requireSummary(reportId);
        boolean reverted;
        if (ReportTypes.CLIENT_FEEDBACK.equals(report.reportType()) && report.clientFeedbackId() != null) {
            reverted = feedbackCommandApi.revertClientFeedback(report.clientFeedbackId());
        } else if (ReportTypes.SERVICE_FEEDBACK.equals(report.reportType())
                && report.serviceFeedbackId() != null) {
            reverted = feedbackCommandApi.revertServiceFeedback(report.serviceFeedbackId());
        } else {
            throw new InvalidStateException("El reporte " + reportId + " no referencia un feedback vigente");
        }
        if (!reverted) {
            throw new InvalidStateException("El feedback del reporte " + reportId + " ya no existe (¿revertido?)");
        }
        reportCommandApi.resolveReport(reportId, adminId, ReportActions.REVERT_FEEDBACK);
    }

    @Override
    @Transactional
    public void removeFeedbackDirectly(RemoveFeedbackCommand command) {
        // Atajo del admin: se auto-reporta (crea el reporte + link) y lo resuelve reutilizando el revert.
        // targetType debe ser el nombre exacto del tipo: SERVICE_FEEDBACK o CLIENT_FEEDBACK.
        Long reportId;
        if (ReportTypes.CLIENT_FEEDBACK.equals(command.targetType())) {
            reportId = reportCommandApi.createClientFeedbackReport(
                    command.adminId(), command.reportedUserId(), command.category(), command.reason(),
                    command.targetId());
        } else if (ReportTypes.SERVICE_FEEDBACK.equals(command.targetType())) {
            reportId = reportCommandApi.createServiceFeedbackReport(
                    command.adminId(), command.reportedUserId(), command.category(), command.reason(),
                    command.targetId());
        } else {
            throw new InvalidStateException(
                    "targetType debe ser SERVICE_FEEDBACK o CLIENT_FEEDBACK: " + command.targetType());
        }
        revertFeedbackFromReport(reportId, command.adminId());
    }

    @Override
    @Transactional
    public void markRequestAsNotProvided(Long reportId, Long adminId) {
        ReportSummarySnapshot report = requireSummary(reportId);
        if (!ReportTypes.REQUEST.equals(report.reportType()) || report.requestId() == null) {
            throw new InvalidStateException("El reporte " + reportId + " no referencia una solicitud");
        }
        requestCommandApi.markAsNotProvided(report.requestId(), adminId);
        reportCommandApi.resolveReport(reportId, adminId, ReportActions.MARK_REQUEST_NOT_PROVIDED);
    }

    @Override
    @Transactional
    public void closeReport(Long reportId, Long adminId) {
        reportCommandApi.closeReport(reportId, adminId);
    }

    private ReportSummarySnapshot requireSummary(Long reportId) {
        return reportQueryApi.findSummary(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reporte no encontrado: " + reportId));
    }
}
