package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.admin.application.dto.command.RemoveFeedbackCommand;
import com.parosurvivors.serviya.admin.application.ports.input.ModerationServicePort;
import com.parosurvivors.serviya.feedback.application.ports.input.ClientFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackServicePort;
import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.command.CreateServiceFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.ports.input.ClientFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ServiceFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.domain.ReportActionType;
import com.parosurvivors.serviya.reports.domain.ReportSummary;
import com.parosurvivors.serviya.reports.domain.ReportType;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.application.ports.input.UserServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestador de moderación (rol ADMIN, módulo 9). Cada método ejecuta su acción de dominio (advertir/
 * banear/revertir feedback/marcar no prestada) y luego DELEGA la finalización del reporte en
 * {@link ReportServicePort#resolveReport}/{@link ReportServicePort#closeReport}, que se encargan de la
 * transición de estado + registrar la {@link ReportActionType} + notificar al reporter. Las notificaciones
 * al usuario reportado (advertido/suspendido) sí las emite este servicio, pues son la acción en sí.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 9).
 */
@Component
@RequiredArgsConstructor
public class ModerationService implements ModerationServicePort {

    private final ReportServicePort reportServicePort;
    private final ServiceFeedbackServicePort serviceFeedbackServicePort;
    private final ClientFeedbackServicePort clientFeedbackServicePort;
    private final ServiceFeedbackReportServicePort serviceFeedbackReportServicePort;
    private final ClientFeedbackReportServicePort clientFeedbackReportServicePort;
    private final ServiceRequestCommandServicePort serviceRequestCommandServicePort;
    private final UserServicePort userServicePort;
    private final NotificationServicePort notificationServicePort;
    /** B5: anti auto-accion y guard de ultimo admin activo. */
    private final AdminActionGuard adminActionGuard;

    @Override
    @Transactional
    public void warnUser(Long reportId, Long adminId) {
        ReportSummary report = reportServicePort.getReportSummary(reportId);
        // La advertencia es la propia notificación al reportado; no hay cambio de estado sobre la cuenta.
        notificationServicePort.notify(
                report.reportedUserId(),
                "USER_WARNED",
                "Has recibido una advertencia",
                "Un administrador revisó un reporte en tu contra (categoría \"" + report.category()
                        + "\") y te ha advertido. Reincidir puede llevar a la suspensión de tu cuenta.",
                "REPORT",
                reportId,
                null,
                null);
        reportServicePort.resolveReport(reportId, adminId, ReportActionType.WARN);
    }

    @Override
    @Transactional
    public void banUserFromReport(Long reportId, Long adminId, String reason) {
        ReportSummary report = reportServicePort.getReportSummary(reportId);
        // B5: el admin no puede banearse a si mismo ni dejar al sistema sin administradores activos.
        adminActionGuard.requireNotSelf(adminId, report.reportedUserId());
        adminActionGuard.requireAnotherActiveAdmin(report.reportedUserId());
        // Motivo del admin si lo escribió; si no, se deriva de la categoría del reporte (nunca el texto
        // libre del reportante, que es la acusación y podría venir manipulada u ofensiva).
        String finalReason = (reason != null && !reason.isBlank())
                ? reason
                : "reporte en tu contra (categoría \"" + report.category() + "\")";
        userServicePort.banUser(report.reportedUserId(), finalReason);
        reportServicePort.resolveReport(reportId, adminId, ReportActionType.BAN);
    }

    @Override
    @Transactional
    public void revertFeedbackFromReport(Long reportId, Long adminId) {
        ReportSummary report = reportServicePort.getReportSummary(reportId);
        boolean reverted;
        if (report.reportType() == ReportType.CLIENT_FEEDBACK && report.clientFeedbackId() != null) {
            reverted = clientFeedbackServicePort.revertFeedbackById(report.clientFeedbackId());
        } else if (report.reportType() == ReportType.SERVICE_FEEDBACK && report.serviceFeedbackId() != null) {
            reverted = serviceFeedbackServicePort.revertFeedbackById(report.serviceFeedbackId());
        } else {
            throw new InvalidStateException("El reporte " + reportId + " no referencia un feedback vigente");
        }
        if (!reverted) {
            throw new InvalidStateException("El feedback del reporte " + reportId + " ya no existe (¿revertido?)");
        }
        reportServicePort.resolveReport(reportId, adminId, ReportActionType.REVERT_FEEDBACK);
    }

    @Override
    @Transactional
    public void removeFeedbackDirectly(RemoveFeedbackCommand command) {
        // Atajo del admin: se auto-reporta (crea el reporte + link) y lo resuelve reutilizando el revert.
        // targetType debe ser el nombre exacto del tipo: SERVICE_FEEDBACK o CLIENT_FEEDBACK.
        Long reportId;
        if ("CLIENT_FEEDBACK".equals(command.targetType())) {
            reportId = clientFeedbackReportServicePort.createReport(new CreateClientFeedbackReportCommand(
                    command.adminId(), command.reportedUserId(), command.category(), command.reason(),
                    command.targetId())).getReportId();
        } else if ("SERVICE_FEEDBACK".equals(command.targetType())) {
            reportId = serviceFeedbackReportServicePort.createReport(new CreateServiceFeedbackReportCommand(
                    command.adminId(), command.reportedUserId(), command.category(), command.reason(),
                    command.targetId())).getReportId();
        } else {
            throw new InvalidStateException(
                    "targetType debe ser SERVICE_FEEDBACK o CLIENT_FEEDBACK: " + command.targetType());
        }
        revertFeedbackFromReport(reportId, command.adminId());
    }

    @Override
    @Transactional
    public void markRequestAsNotProvided(Long reportId, Long adminId) {
        ReportSummary report = reportServicePort.getReportSummary(reportId);
        if (report.reportType() != ReportType.REQUEST || report.requestId() == null) {
            throw new InvalidStateException("El reporte " + reportId + " no referencia una solicitud");
        }
        serviceRequestCommandServicePort.markAsNotProvided(report.requestId(), adminId);
        reportServicePort.resolveReport(reportId, adminId, ReportActionType.MARK_REQUEST_NOT_PROVIDED);
    }

    @Override
    @Transactional
    public void closeReport(Long reportId, Long adminId) {
        reportServicePort.closeReport(reportId, adminId);
    }
}
