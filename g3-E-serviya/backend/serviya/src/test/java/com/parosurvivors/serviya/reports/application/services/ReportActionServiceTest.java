package com.parosurvivors.serviya.reports.application.services;

import com.parosurvivors.serviya.reports.application.ports.output.ReportActionPersistencePort;
import com.parosurvivors.serviya.reports.application.ports.output.ReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.domain.ReportActionType;
import com.parosurvivors.serviya.reports.domain.ReportStatus;
import com.parosurvivors.serviya.reports.domain.ReportType;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportActionServiceTest {

    @Mock
    private ReportActionPersistencePort reportActionPersistencePort;

    @Mock
    private ReportPersistencePort reportPersistencePort;

    @InjectMocks
    private ReportActionService service;

    private Report report(Long id) {
        return Report.builder()
                .id(id)
                .reporterId(8L)
                .reportedUserId(45L)
                .reportType(ReportType.REQUEST)
                .category("HARASSMENT")
                .reason("razón")
                .status(ReportStatus.PENDING)
                .build();
    }

    @Test
    void createActionGeneratesPreestablishedDescriptionSnapshotAndPersists() {
        when(reportPersistencePort.findById(3L)).thenReturn(Optional.of(report(3L)));
        when(reportActionPersistencePort.save(any(ReportAction.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportAction result = service.createAction(3L, 12L, ReportActionType.BAN);

        ArgumentCaptor<ReportAction> captor = ArgumentCaptor.forClass(ReportAction.class);
        verify(reportActionPersistencePort).save(captor.capture());
        ReportAction saved = captor.getValue();

        assertThat(saved.getReportId()).isEqualTo(3L);
        assertThat(saved.getAdminId()).isEqualTo(12L);
        assertThat(saved.getActionType()).isEqualTo(ReportActionType.BAN);
        assertThat(saved.getCreatedAt()).isNotNull();
        // El snapshot embebe: acción, admin, reportado, reportante, reporte origen y categoría.
        assertThat(saved.getActionDescription())
                .contains("[BAN]")
                .contains("#12")   // admin
                .contains("#45")   // reportado
                .contains("#8")    // reportante
                .contains("#3")    // reporte
                .contains("HARASSMENT");
        assertThat(result.getActionDescription()).isEqualTo(saved.getActionDescription());
    }

    @Test
    void createActionThrowsWhenReportDoesNotExist() {
        when(reportPersistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAction(99L, 1L, ReportActionType.WARN))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(reportActionPersistencePort);
    }

    @Test
    void getActionsByReportReturnsChronologicalOrder() {
        ReportAction newer = ReportAction.builder()
                .id(2L).reportId(3L).actionType(ReportActionType.CLOSE)
                .createdAt(LocalDateTime.of(2026, 7, 13, 12, 0)).build();
        ReportAction older = ReportAction.builder()
                .id(1L).reportId(3L).actionType(ReportActionType.WARN)
                .createdAt(LocalDateTime.of(2026, 7, 13, 10, 0)).build();
        when(reportActionPersistencePort.findByReportId(3L)).thenReturn(List.of(newer, older));

        List<ReportAction> result = service.getActionsByReport(3L);

        assertThat(result).extracting(ReportAction::getId).containsExactly(1L, 2L);
    }
}
