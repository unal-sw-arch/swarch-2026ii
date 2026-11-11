package com.parosurvivors.serviya.reports.application.services;

import com.parosurvivors.serviya.notifications.api.Channel;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.application.ports.input.ClientFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.ClientFeedbackReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.shared.contract.Roles;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ClientFeedbackReportService implements ClientFeedbackReportServicePort {

    private static final String NOTIFICATION_TYPE = "CLIENT_FEEDBACK_REPORT_CREATED";

    private final ClientFeedbackReportPersistencePort clientFeedbackReportPersistencePort;
    private final ReportServicePort reportServicePort;
    private final UserQueryApi userQueryApi;
    private final NotificationCommandApi notificationCommandApi;

    @Override
    @Transactional
    public ClientFeedbackReport createReport(CreateClientFeedbackReportCommand command) {
        Report baseReport = reportServicePort.createBaseReport(
                command.reporterId(),
                command.reportedUserId(),
                "CLIENT_FEEDBACK",
                command.category(),
                command.reason());

        ClientFeedbackReport link = ClientFeedbackReport.builder()
                .reportId(baseReport.getId())
                .feedbackId(command.clientFeedbackId())
                .build();
        ClientFeedbackReport saved = clientFeedbackReportPersistencePort.save(link);

        notifyAdmins(baseReport.getId());
        return saved;
    }

    /** Avisa a la cola de administradores para que el reporte entre en moderacion (queda Pending). */
    private void notifyAdmins(Long reportId) {
        List<Long> adminIds = userQueryApi.findUserIdsByRole(Roles.ADMIN);
        for (Long adminId : adminIds) {
            notificationCommandApi.notify(new NotificationCommand(
                    adminId,
                    NOTIFICATION_TYPE,
                    "Nuevo reporte de reseña de cliente",
                    "Se reporto una reseña de cliente. Requiere revision.",
                    "REPORT",
                    reportId,
                    Set.of(Channel.INTERNAL),
                    Map.of()));
        }
    }

    @Override
    public ReportDetailResult getReportDetail(Long reportId) {
        return reportServicePort.getReportDetail(reportId);
    }
}
