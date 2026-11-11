package com.parosurvivors.serviya.reports.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.feedback.api.dto.ServiceFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.api.dto.TagCatalogEntry;
import com.parosurvivors.serviya.notifications.api.Channel;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.application.ports.input.ReportActionServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.ClientFeedbackReportPersistencePort;
import com.parosurvivors.serviya.reports.application.ports.output.ReportPersistencePort;
import com.parosurvivors.serviya.reports.application.ports.output.RequestReportPersistencePort;
import com.parosurvivors.serviya.reports.application.ports.output.ServiceFeedbackReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportActionType;
import com.parosurvivors.serviya.reports.domain.ReportStatus;
import com.parosurvivors.serviya.reports.domain.ReportSummary;
import com.parosurvivors.serviya.reports.domain.ReportType;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import com.parosurvivors.serviya.requests.api.dto.RequestDetailSnapshot;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long REQUEST_ID = 99L;
    private static final Long SERVICE_ID = 7L;
    private static final Long ADDRESS_ID = 3L;

    @Mock private ReportPersistencePort reportPersistencePort;
    @Mock private RequestReportPersistencePort requestReportPersistencePort;
    @Mock private ServiceFeedbackReportPersistencePort serviceFeedbackReportPersistencePort;
    @Mock private ClientFeedbackReportPersistencePort clientFeedbackReportPersistencePort;
    @Mock private ProfileQueryApi profileQueryApi;
    @Mock private RequestQueryApi requestQueryApi;
    @Mock private ServiceQueryApi serviceQueryApi;
    @Mock private FeedbackQueryApi feedbackQueryApi;
    @Mock private ReportActionServicePort reportActionServicePort;
    @Mock private NotificationCommandApi notificationCommandApi;

    @InjectMocks
    private ReportService service;

    @Test
    void getReportDetailEnrichesPartiesAndRequestPayload() {
        when(reportPersistencePort.findById(1L)).thenReturn(Optional.of(report(1L, ReportType.REQUEST)));
        stubParties();
        when(requestReportPersistencePort.findByReportId(1L))
                .thenReturn(Optional.of(RequestReport.builder().reportId(1L).requestId(REQUEST_ID).build()));
        when(requestQueryApi.findDetail(REQUEST_ID)).thenReturn(Optional.of(requestDetail()));
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(service()));
        when(profileQueryApi.findAddress(ADDRESS_ID)).thenReturn(Optional.of(address()));

        ReportDetailResult result = service.getReportDetail(1L);

        assertThat(result.reporter().fullName()).isEqualTo("Rep Orter");
        assertThat(result.reporter().photoUrl()).isEqualTo("r.png");
        assertThat(result.reported().fullName()).isEqualTo("Report Ed");
        assertThat(result.request().requestId()).isEqualTo(REQUEST_ID);
        assertThat(result.request().serviceTitle()).isEqualTo("Plomería a domicilio");
        assertThat(result.request().status()).isEqualTo("PENDING");
        assertThat(result.request().city()).isEqualTo("Bogotá");
        assertThat(result.feedback()).isNull();
    }

    @Test
    void getReportDetailEnrichesServiceFeedbackContent() {
        when(reportPersistencePort.findById(2L)).thenReturn(Optional.of(report(2L, ReportType.SERVICE_FEEDBACK)));
        stubParties();
        when(serviceFeedbackReportPersistencePort.findByReportId(2L))
                .thenReturn(Optional.of(ServiceFeedbackReport.builder().reportId(2L).feedbackId(77L).build()));
        when(feedbackQueryApi.findServiceFeedback(77L)).thenReturn(Optional.of(
                new ServiceFeedbackSnapshot(77L, REQUEST_ID, SERVICE_ID, 8L, 1, "pésimo",
                        List.of(10L), LocalDateTime.now())));
        when(feedbackQueryApi.getServiceTags()).thenReturn(List.of(
                new TagCatalogEntry(10L, "impuntual", false)));

        ReportDetailResult result = service.getReportDetail(2L);

        assertThat(result.request()).isNull();
        assertThat(result.feedback().kind()).isEqualTo("SERVICE");
        assertThat(result.feedback().feedbackId()).isEqualTo(77L);
        assertThat(result.feedback().rating()).isEqualTo(1);
        assertThat(result.feedback().comment()).isEqualTo("pésimo");
        assertThat(result.feedback().tags()).containsExactly("impuntual");
    }

    @Test
    void getReportDetailForRevertedFeedbackReturnsShellWithIdAndKindOnly() {
        when(reportPersistencePort.findById(3L)).thenReturn(Optional.of(report(3L, ReportType.CLIENT_FEEDBACK)));
        stubParties();
        when(clientFeedbackReportPersistencePort.findByReportId(3L))
                .thenReturn(Optional.of(ClientFeedbackReport.builder().reportId(3L).feedbackId(55L).build()));
        when(feedbackQueryApi.findClientFeedback(55L)).thenReturn(Optional.empty());

        ReportDetailResult result = service.getReportDetail(3L);

        assertThat(result.feedback().kind()).isEqualTo("CLIENT");
        assertThat(result.feedback().feedbackId()).isEqualTo(55L);
        assertThat(result.feedback().rating()).isNull();
        assertThat(result.feedback().comment()).isNull();
        assertThat(result.feedback().tags()).isEmpty();
    }

    @Test
    void getReportDetailThrowsWhenReportMissing() {
        when(reportPersistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReportDetail(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getReportSummaryReturnsBaseFieldsAndRequestIdWithoutEnrichment() {
        when(reportPersistencePort.findById(10L)).thenReturn(Optional.of(report(10L, ReportType.REQUEST)));
        when(requestReportPersistencePort.findByReportId(10L))
                .thenReturn(Optional.of(RequestReport.builder().reportId(10L).requestId(REQUEST_ID).build()));

        ReportSummary summary = service.getReportSummary(10L);

        assertThat(summary.reportType()).isEqualTo(ReportType.REQUEST);
        assertThat(summary.reportedUserId()).isEqualTo(45L);
        assertThat(summary.requestId()).isEqualTo(REQUEST_ID);
        assertThat(summary.serviceFeedbackId()).isNull();
        verifyNoInteractions(profileQueryApi, requestQueryApi);
    }

    @Test
    void getReportSummaryReturnsServiceFeedbackId() {
        when(reportPersistencePort.findById(11L)).thenReturn(Optional.of(report(11L, ReportType.SERVICE_FEEDBACK)));
        when(serviceFeedbackReportPersistencePort.findByReportId(11L))
                .thenReturn(Optional.of(ServiceFeedbackReport.builder().reportId(11L).feedbackId(77L).build()));

        ReportSummary summary = service.getReportSummary(11L);

        assertThat(summary.serviceFeedbackId()).isEqualTo(77L);
        assertThat(summary.requestId()).isNull();
        verifyNoInteractions(feedbackQueryApi);
    }

    @Test
    void resolveReportSetsResolvedRecordsActionAndNotifiesReporter() {
        when(reportPersistencePort.findById(5L)).thenReturn(Optional.of(report(5L, ReportType.REQUEST)));

        service.resolveReport(5L, 12L, ReportActionType.BAN);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportPersistencePort).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(reportActionServicePort).createAction(5L, 12L, ReportActionType.BAN);
        ArgumentCaptor<NotificationCommand> notification = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationCommandApi).notify(notification.capture());
        assertThat(notification.getValue().userId()).isEqualTo(8L);
        assertThat(notification.getValue().type()).isEqualTo("REPORT_RESOLVED");
        assertThat(notification.getValue().message()).contains("se suspendió");
        assertThat(notification.getValue().entityType()).isEqualTo("REPORT");
        assertThat(notification.getValue().entityId()).isEqualTo(5L);
        assertThat(notification.getValue().channels()).containsExactly(Channel.INTERNAL);
    }

    @Test
    void closeReportSetsClosedRecordsCloseActionAndNotifiesReporter() {
        when(reportPersistencePort.findById(6L)).thenReturn(Optional.of(report(6L, ReportType.REQUEST)));

        service.closeReport(6L, 12L);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportPersistencePort).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.CLOSED);
        verify(reportActionServicePort).createAction(6L, 12L, ReportActionType.CLOSE);
        ArgumentCaptor<NotificationCommand> notification = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationCommandApi).notify(notification.capture());
        assertThat(notification.getValue().userId()).isEqualTo(8L);
        assertThat(notification.getValue().type()).isEqualTo("REPORT_CLOSED");
        assertThat(notification.getValue().channels()).containsExactly(Channel.INTERNAL);
    }

    @Test
    void resolveReportOnClosedReportFailsWithoutSideEffects() {
        Report closed = Report.builder().id(7L).reporterId(8L).reportedUserId(45L)
                .reportType(ReportType.REQUEST).category("x").reason("y").status(ReportStatus.CLOSED).build();
        when(reportPersistencePort.findById(7L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.resolveReport(7L, 12L, ReportActionType.WARN))
                .isInstanceOf(InvalidStateException.class);

        verify(reportPersistencePort, never()).update(any());
        verify(reportActionServicePort, never()).createAction(anyLong(), anyLong(), any());
        verify(notificationCommandApi, never()).notify(any());
    }

    private Report report(Long id, ReportType type) {
        return Report.builder()
                .id(id).reporterId(8L).reportedUserId(45L).reportType(type)
                .category("HARASSMENT").reason("razón").status(ReportStatus.PENDING)
                .build();
    }

    private void stubParties() {
        when(profileQueryApi.findProfile(8L)).thenReturn(Optional.of(profile(8L, "Rep Orter", "r.png")));
        when(profileQueryApi.findProfile(45L)).thenReturn(Optional.of(profile(45L, "Report Ed", "e.png")));
    }

    private ProfileSnapshot profile(Long userId, String fullName, String photoUrl) {
        return new ProfileSnapshot(null, userId, fullName, null, null, null,
                null, photoUrl, null, null, null);
    }

    private RequestDetailSnapshot requestDetail() {
        return new RequestDetailSnapshot(
                REQUEST_ID, "PENDING", LocalDateTime.of(2026, 8, 1, 9, 0),
                new BigDecimal("50000"), null, null, null, null, null,
                SERVICE_ID, 8L, 45L, ADDRESS_ID);
    }

    private ServiceSnapshot service() {
        return new ServiceSnapshot(SERVICE_ID, 45L, "Plomería a domicilio", null,
                List.of(), new BigDecimal("30000"), 1L, 60, true, null, null, null, null);
    }

    private AddressSnapshot address() {
        return new AddressSnapshot(ADDRESS_ID, 8L, null, "Bogotá", null, null, null);
    }
}
