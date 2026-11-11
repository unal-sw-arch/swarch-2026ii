package com.parosurvivors.serviya.reports.application.services;

import com.parosurvivors.serviya.notifications.api.Channel;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.RequestReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.RequestReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import com.parosurvivors.serviya.requests.api.dto.RequestDetailSnapshot;
import com.parosurvivors.serviya.shared.contract.Roles;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;
import com.parosurvivors.serviya.shared.textfilter.application.ports.output.WordFilterPort;
import com.parosurvivors.serviya.users.api.UserQueryApi;
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
    private final RequestQueryApi requestQueryApi;
    private final UserQueryApi userQueryApi;
    private final NotificationCommandApi notificationCommandApi;
    private final WordFilterPort wordFilterPort;

    /**
     * Crea el reporte vinculado a la solicitud y a la parte reportada, y avisa a los administradores.
     *
     * <p>Reglas aplicadas:
     * <ul>
     *   <li><b>Ownership</b>: {@code RequestQueryApi.findDetail} + comprobacion de participante
     *       lanza ForbiddenException si quien reporta no es el cliente ni el oferente de esa
     *       solicitud, asi que solo las partes pueden reportarla.</li>
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
        RequestDetailSnapshot request = requestQueryApi.findDetail(command.requestId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Solicitud no encontrada: " + command.requestId()));
        boolean isParticipant = command.reporterId().equals(request.clientId())
                || command.reporterId().equals(request.offererId());
        if (!isParticipant) {
            throw new ForbiddenException("El usuario no participa en la solicitud");
        }

        Long reportedUserId = command.reporterId().equals(request.clientId())
                ? request.offererId()
                : request.clientId();

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
        List<Long> adminIds = userQueryApi.findUserIdsByRole(Roles.ADMIN);
        for (Long adminId : adminIds) {
            notificationCommandApi.notify(new NotificationCommand(
                    adminId,
                    NOTIFICATION_TYPE,
                    "Nuevo reporte de incumplimiento",
                    "Se reporto un incumplimiento en la solicitud #" + requestId + ". Requiere revision.",
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
