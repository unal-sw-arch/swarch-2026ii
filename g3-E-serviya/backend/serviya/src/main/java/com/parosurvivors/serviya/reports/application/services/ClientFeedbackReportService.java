package com.parosurvivors.serviya.reports.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.application.ports.input.ClientFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.ClientFeedbackReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
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
    private final UserRoleServicePort userRoleServicePort;
    private final NotificationServicePort notificationServicePort;

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
        List<Long> adminIds = userRoleServicePort.findUserIdsByRole(RoleName.ADMIN);
        for (Long adminId : adminIds) {
            notificationServicePort.notify(
                    adminId,
                    NOTIFICATION_TYPE,
                    "Nuevo reporte de reseña de cliente",
                    "Se reporto una reseña de cliente. Requiere revision.",
                    "REPORT",
                    reportId,
                    Set.of(ChannelName.INTERNAL),
                    Map.of());
        }
    }

    @Override
    public ReportDetailResult getReportDetail(Long reportId) {
        return reportServicePort.getReportDetail(reportId);
    }
}
