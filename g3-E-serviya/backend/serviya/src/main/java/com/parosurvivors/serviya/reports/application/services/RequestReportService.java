package com.parosurvivors.serviya.reports.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.RequestReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.RequestReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.requests.application.dto.result.ServiceRequestDetailResult;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.shared.textfilter.application.ports.output.WordFilterPort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporte de incumplimiento de una solicitud (RF-073): el cliente o el oferente denuncian que la otra
 * parte no cumplio lo acordado (no se presento, no estaba en casa...).
 *
 * <p>Esta HU cubre UNICAMENTE la CREACION del reporte. Su resolucion por parte del administrador
 * —incluido marcar la solicitud como NOT_PROVIDED— es RF-074 y vive en el modulo de moderacion.</p>
 */
@Component
@RequiredArgsConstructor
public class RequestReportService implements RequestReportServicePort {

    private static final String NOTIFICATION_TYPE = "REQUEST_REPORT_CREATED";

    private final RequestReportPersistencePort requestReportPersistencePort;
    private final ReportServicePort reportServicePort;
    private final ServiceRequestQueryServicePort serviceRequestQueryServicePort;
    private final UserRoleServicePort userRoleServicePort;
    private final NotificationServicePort notificationServicePort;
    private final WordFilterPort wordFilterPort;

    /**
     * Crea el reporte vinculado a la solicitud y a la parte reportada, y avisa a los administradores.
     *
     * <p>Reglas aplicadas:
     * <ul>
     *   <li><b>Ownership</b>: {@code getRequestDetailForParty} lanza UnauthorizedException si quien
     *       reporta no es el cliente ni el oferente de esa solicitud, asi que solo las partes pueden
     *       reportarla.</li>
     *   <li><b>Reportado derivado, no confiado</b>: el usuario reportado es la CONTRAPARTE que
     *       devuelve la solicitud, nunca el {@code reportedUserId} que venga del cliente (que podria
     *       manipularse para incriminar a un tercero).</li>
     *   <li><b>Filtro de palabras</b> (RNF-006) sobre el motivo antes de persistir.</li>
     *   <li><b>Atomica</b>: escribe en {@code reports} y {@code request_reports}; si falla el enlace
     *       no puede quedar un reporte base huerfano.</li>
     * </ul>
     */
    @Override
    @Transactional
    public RequestReport createReport(CreateRequestReportCommand command) {
        // Ownership + contraparte real de la solicitud (lanza si el reportante no participa).
        ServiceRequestDetailResult request = serviceRequestQueryServicePort
                .getRequestDetailForParty(command.requestId(), command.reporterId());

        Long reportedUserId = request.counterpartyId();

        Report baseReport = reportServicePort.createBaseReport(
                command.reporterId(),
                reportedUserId,
                "REQUEST",
                command.category(),
                wordFilterPort.filter(command.reason()));

        RequestReport link = RequestReport.builder()
                .reportId(baseReport.getId())
                .requestId(command.requestId())
                .build();
        RequestReport saved = requestReportPersistencePort.save(link);

        notifyAdmins(baseReport.getId(), command.requestId());
        return saved;
    }

    /** Avisa a la cola de administradores para que el reporte entre en moderacion (queda Pending). */
    private void notifyAdmins(Long reportId, Long requestId) {
        List<Long> adminIds = userRoleServicePort.findUserIdsByRole(RoleName.ADMIN);
        for (Long adminId : adminIds) {
            notificationServicePort.notify(
                    adminId,
                    NOTIFICATION_TYPE,
                    "Nuevo reporte de incumplimiento",
                    "Se reporto un incumplimiento en la solicitud #" + requestId + ". Requiere revision.",
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
