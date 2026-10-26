package com.parosurvivors.serviya.reports.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.RequestReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.requests.application.dto.result.ServiceRequestDetailResult;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import com.parosurvivors.serviya.shared.textfilter.application.ports.output.WordFilterPort;
import com.parosurvivors.serviya.users.application.ports.input.UserRoleServicePort;
import com.parosurvivors.serviya.users.domain.RoleName;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RF-073: reporte de incumplimiento de una solicitud.
 *
 * <p>Nota: la version anterior de esta prueba fijaba el comportamiento inseguro (el usuario reportado
 * se tomaba del body y no se verificaba que quien reporta participara en la solicitud). Se reescribe
 * para cubrir las reglas correctas: ownership, reportado derivado, filtro de palabras y aviso a admins.</p>
 */
@ExtendWith(MockitoExtension.class)
class RequestReportServiceTest {

    private static final Long REPORTER_ID = 1L;        // el cliente de la solicitud
    private static final Long COUNTERPARTY_ID = 2L;    // el oferente: la parte realmente reportable
    private static final Long REQUEST_ID = 99L;
    private static final Long ADMIN_ID = 7L;

    @Mock RequestReportPersistencePort requestReportPersistencePort;
    @Mock ReportServicePort reportServicePort;
    @Mock ServiceRequestQueryServicePort serviceRequestQueryServicePort;
    @Mock UserRoleServicePort userRoleServicePort;
    @Mock NotificationServicePort notificationServicePort;
    @Mock WordFilterPort wordFilterPort;

    @InjectMocks RequestReportService service;

    private ServiceRequestDetailResult requestWhereReporterIsClient() {
        return new ServiceRequestDetailResult(
                REQUEST_ID, RequestStatus.ACCEPTED, null, BigDecimal.TEN, null, null, null, null,
                1L, "Plomeria", "Hogar", BigDecimal.TEN, 60,
                COUNTERPARTY_ID, "Carlos", null,
                1L, "Calle 1", "Bogota", null, null);
    }

    private void happyPathStubs() {
        when(serviceRequestQueryServicePort.getRequestDetailForParty(REQUEST_ID, REPORTER_ID))
                .thenReturn(requestWhereReporterIsClient());
        when(reportServicePort.createBaseReport(any(), any(), any(), any(), any()))
                .thenReturn(Report.builder().id(10L).build());
        when(requestReportPersistencePort.save(any(RequestReport.class)))
                .thenReturn(RequestReport.builder().id(55L).reportId(10L).requestId(REQUEST_ID).build());
        when(userRoleServicePort.findUserIdsByRole(RoleName.ADMIN)).thenReturn(List.of(ADMIN_ID));
    }

    @Test
    void crea_el_reporte_y_lo_enlaza_a_la_solicitud() {
        happyPathStubs();
        when(wordFilterPort.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        RequestReport result = service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID));

        assertThat(result.getReportId()).isEqualTo(10L);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID);
    }

    /** Ownership: quien no participa en la solicitud no puede reportarla. */
    @Test
    void solo_las_partes_de_la_solicitud_pueden_reportarla() {
        when(serviceRequestQueryServicePort.getRequestDetailForParty(REQUEST_ID, REPORTER_ID))
                .thenThrow(new UnauthorizedException("El usuario no participa en la solicitud"));

        assertThatThrownBy(() -> service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID)))
                .isInstanceOf(UnauthorizedException.class);

        verify(reportServicePort, never()).createBaseReport(any(), any(), any(), any(), any());
        verify(requestReportPersistencePort, never()).save(any());
    }

    /**
     * Seguridad: el reportado se DERIVA de la contraparte real. Aunque el cliente mande otro id en el
     * body para incriminar a un tercero, ese id se ignora.
     */
    @Test
    void el_usuario_reportado_se_deriva_de_la_solicitud_y_no_del_body() {
        happyPathStubs();
        when(wordFilterPort.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        // El command ya ni siquiera admite un reportedUserId: el cliente no puede proponerlo.
        service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID));

        verify(reportServicePort).createBaseReport(
                eq(REPORTER_ID), eq(COUNTERPARTY_ID), eq("REQUEST"), any(), any());
    }

    /** RNF-006: el motivo se censura antes de persistir. */
    @Test
    void aplica_el_filtro_de_palabras_al_motivo() {
        happyPathStubs();
        when(wordFilterPort.filter("el idiota no vino")).thenReturn("el *** no vino");

        service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "el idiota no vino", REQUEST_ID));

        verify(reportServicePort).createBaseReport(any(), any(), any(), any(), eq("el *** no vino"));
    }

    /** El reporte debe llegar a la cola de administradores para entrar en moderacion. */
    @Test
    void notifica_a_los_administradores() {
        happyPathStubs();
        when(wordFilterPort.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID));

        verify(notificationServicePort).notify(
                eq(ADMIN_ID), anyString(), anyString(), anyString(), eq("REPORT"), eq(10L),
                anySet(), any());
    }
}
