package com.parosurvivors.serviya.reports.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.reports.api.dto.ReportActionSnapshot;
import com.parosurvivors.serviya.reports.api.dto.ReportSnapshot;
import com.parosurvivors.serviya.reports.application.ports.input.ReportActionServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ServiceFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ClientFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.ReportActionPersistencePort;
import com.parosurvivors.serviya.reports.application.ports.output.ReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.domain.ReportActionType;
import com.parosurvivors.serviya.reports.domain.ReportPriority;
import com.parosurvivors.serviya.reports.domain.ReportStatus;
import com.parosurvivors.serviya.reports.domain.ReportSummary;
import com.parosurvivors.serviya.reports.domain.ReportType;
import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ReportApiFacadeTest {

    @Mock private ReportPersistencePort reportPersistencePort;
    @Mock private ReportActionPersistencePort reportActionPersistencePort;
    @Mock private ReportServicePort reportServicePort;
    @Mock private ReportActionServicePort reportActionServicePort;
    @Mock private ServiceFeedbackReportServicePort serviceFeedbackReportServicePort;
    @Mock private ClientFeedbackReportServicePort clientFeedbackReportServicePort;

    @InjectMocks private ReportApiFacade facade;

    @Test
    void findByIdMapsReport() {
        when(reportPersistencePort.findById(1L)).thenReturn(Optional.of(report()));

        ReportSnapshot snapshot = facade.findById(1L).orElseThrow();

        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.reportType()).isEqualTo("REQUEST");
        assertThat(snapshot.status()).isEqualTo("PENDING");
        assertThat(snapshot.priority()).isEqualTo("HIGH");
    }

    @Test
    void findSummaryMapsTargetIds() {
        ReportSummary summary = new ReportSummary(
                1L, 10L, 20L, ReportType.REQUEST, "spam",
                ReportStatus.PENDING, 30L, null, null);
        when(reportServicePort.getReportSummary(1L)).thenReturn(summary);

        var result = facade.findSummary(1L).orElseThrow();

        assertThat(result.requestId()).isEqualTo(30L);
        assertThat(result.reportType()).isEqualTo("REQUEST");
    }

    @Test
    void findSummaryReturnsEmptyWhenMissing() {
        when(reportServicePort.getReportSummary(99L))
                .thenThrow(new ResourceNotFoundException("not found"));

        assertThat(facade.findSummary(99L)).isEmpty();
    }

    @Test
    void searchMapsPage() {
        when(reportPersistencePort.findReports(any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(report())));

        var result = facade.search("REQUEST", "spam", "PENDING", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(1L);
    }

    @Test
    void getActionsMapsDescriptions() {
        ReportAction action = ReportAction.builder()
                .id(5L).reportId(1L).adminId(9L).actionType(ReportActionType.WARN)
                .actionDescription("[WARN] warning").createdAt(LocalDateTime.now()).build();
        when(reportActionPersistencePort.findByReportId(1L)).thenReturn(List.of(action));

        List<ReportActionSnapshot> actions = facade.getActions(1L);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).actionType()).isEqualTo("WARN");
    }

    @Test
    void countsDelegate() {
        when(reportPersistencePort.countByReportedUserId(20L)).thenReturn(3L);
        when(reportPersistencePort.countByReporterId(10L)).thenReturn(4L);

        assertThat(facade.countByReportedUser(20L)).isEqualTo(3L);
        assertThat(facade.countByReporter(10L)).isEqualTo(4L);
    }

    @Test
    void commandsDelegateAndConvertActionType() {
        when(reportServicePort.createBaseReport(10L, 20L, "REQUEST", "spam", "reason"))
                .thenReturn(report());
        when(reportActionServicePort.createAction(1L, 9L, ReportActionType.WARN))
                .thenReturn(ReportAction.builder()
                        .id(5L).reportId(1L).adminId(9L).actionType(ReportActionType.WARN).build());

        assertThat(facade.createBaseReport(10L, 20L, "REQUEST", "spam", "reason").id()).isEqualTo(1L);
        assertThat(facade.createAction(1L, 9L, "WARN").actionType()).isEqualTo("WARN");

        facade.resolveReport(1L, 9L, "BAN");
        facade.closeReport(1L, 9L);

        verify(reportServicePort).resolveReport(1L, 9L, ReportActionType.BAN);
        verify(reportServicePort).closeReport(1L, 9L);
    }

    @Test
    void rejectsUnknownActionType() {
        assertThatThrownBy(() -> facade.resolveReport(1L, 9L, "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void feedbackReportCreatorsReturnReportId() {
        when(serviceFeedbackReportServicePort.createReport(any()))
                .thenReturn(ServiceFeedbackReport.builder().reportId(7L).build());
        when(clientFeedbackReportServicePort.createReport(any()))
                .thenReturn(ClientFeedbackReport.builder().reportId(8L).build());

        assertThat(facade.createServiceFeedbackReport(1L, 2L, "SPAM", "x", 3L)).isEqualTo(7L);
        assertThat(facade.createClientFeedbackReport(1L, 2L, "SPAM", "x", 4L)).isEqualTo(8L);
    }

    private Report report() {
        return Report.builder()
                .id(1L).reporterId(10L).reportedUserId(20L)
                .reportType(ReportType.REQUEST).category("spam").reason("reason")
                .status(ReportStatus.PENDING).priority(ReportPriority.HIGH)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
