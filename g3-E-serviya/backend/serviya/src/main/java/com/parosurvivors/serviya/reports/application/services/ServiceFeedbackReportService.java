package com.parosurvivors.serviya.reports.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import com.parosurvivors.serviya.reports.application.dto.command.CreateServiceFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ServiceFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.ServiceFeedbackReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;
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
public class ServiceFeedbackReportService implements ServiceFeedbackReportServicePort {

    private static final String NOTIFICATION_TYPE = "SERVICE_FEEDBACK_REPORT_CREATED";

    private final ServiceFeedbackReportPersistencePort serviceFeedbackReportPersistencePort;
    private final ReportServicePort reportServicePort;
    private final UserRoleServicePort userRoleServicePort;
    private final NotificationServicePort notificationServicePort;

    @Override
    @Transactional
    public ServiceFeedbackReport createReport(CreateServiceFeedbackReportCommand command) {
        Report baseReport = reportServicePort.createBaseReport(
                command.reporterId(),
                command.reportedUserId(),
                "SERVICE_FEEDBACK",
                command.category(),
                command.reason());

        ServiceFeedbackReport link = ServiceFeedbackReport.builder()
                .reportId(baseReport.getId())
                .feedbackId(command.serviceFeedbackId())
                .build();
        ServiceFeedbackReport saved = serviceFeedbackReportPersistencePort.save(link);

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
                    "Nuevo reporte de reseña de servicio",
                    "Se reporto una reseña de servicio. Requiere revision.",
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
