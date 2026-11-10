package com.parosurvivors.serviya.reports.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.RequestReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import com.parosurvivors.serviya.requests.api.dto.RequestDetailSnapshot;
import com.parosurvivors.serviya.shared.contract.Roles;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;
import com.parosurvivors.serviya.shared.textfilter.application.ports.output.WordFilterPort;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestReportServiceTest {

    private static final Long REPORTER_ID = 1L;
    private static final Long COUNTERPARTY_ID = 2L;
    private static final Long REQUEST_ID = 99L;
    private static final Long ADMIN_ID = 7L;

    @Mock RequestReportPersistencePort requestReportPersistencePort;
    @Mock ReportServicePort reportServicePort;
    @Mock RequestQueryApi requestQueryApi;
    @Mock UserQueryApi userQueryApi;
    @Mock NotificationCommandApi notificationCommandApi;
    @Mock WordFilterPort wordFilterPort;

    @InjectMocks RequestReportService service;

    private RequestDetailSnapshot requestWhereReporterIsClient() {
        return new RequestDetailSnapshot(
                REQUEST_ID, "ACCEPTED", LocalDateTime.of(2026, 8, 1, 9, 0), BigDecimal.TEN,
                null, null, null, null, null, 1L, REPORTER_ID, COUNTERPARTY_ID, 1L);
    }

    private RequestDetailSnapshot requestWhereReporterIsNotParticipant() {
        return new RequestDetailSnapshot(
                REQUEST_ID, "ACCEPTED", LocalDateTime.of(2026, 8, 1, 9, 0), BigDecimal.TEN,
                null, null, null, null, null, 1L, 5L, 6L, 1L);
    }

    private void happyPathStubs() {
        when(requestQueryApi.findDetail(REQUEST_ID)).thenReturn(Optional.of(requestWhereReporterIsClient()));
        when(reportServicePort.createBaseReport(any(), any(), any(), any(), any()))
                .thenReturn(Report.builder().id(10L).build());
        when(requestReportPersistencePort.save(any(RequestReport.class)))
                .thenReturn(RequestReport.builder().id(55L).reportId(10L).requestId(REQUEST_ID).build());
        when(userQueryApi.findUserIdsByRole(Roles.ADMIN)).thenReturn(List.of(ADMIN_ID));
    }

    @Test
    void creaElReporteYLoEnlazaALaSolicitud() {
        happyPathStubs();
        when(wordFilterPort.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        RequestReport result = service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID));

        assertThat(result.getReportId()).isEqualTo(10L);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void soloLasPartesDeLaSolicitudPuedenReportarla() {
        when(requestQueryApi.findDetail(REQUEST_ID))
                .thenReturn(Optional.of(requestWhereReporterIsNotParticipant()));

        assertThatThrownBy(() -> service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID)))
                .isInstanceOf(ForbiddenException.class);

        verify(reportServicePort, never()).createBaseReport(any(), any(), any(), any(), any());
        verify(requestReportPersistencePort, never()).save(any());
    }

    @Test
    void elUsuarioReportadoSeDerivaDeLaSolicitudYNoDelBody() {
        happyPathStubs();
        when(wordFilterPort.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID));

        verify(reportServicePort).createBaseReport(
                eq(REPORTER_ID), eq(COUNTERPARTY_ID), eq("REQUEST"), any(), any());
    }

    @Test
    void aplicaElFiltroDePalabrasAlMotivo() {
        happyPathStubs();
        when(wordFilterPort.filter("el idiota no vino")).thenReturn("el *** no vino");

        service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "el idiota no vino", REQUEST_ID));

        verify(reportServicePort).createBaseReport(any(), any(), any(), any(), eq("el *** no vino"));
    }

    @Test
    void notificaALosAdministradores() {
        happyPathStubs();
        when(wordFilterPort.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        service.createReport(new CreateRequestReportCommand(
                REPORTER_ID, "Fraude", "No asistio", REQUEST_ID));

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationCommandApi).notify(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(ADMIN_ID);
        assertThat(captor.getValue().type()).isEqualTo("REQUEST_REPORT_CREATED");
        assertThat(captor.getValue().entityId()).isEqualTo(10L);
    }
}
