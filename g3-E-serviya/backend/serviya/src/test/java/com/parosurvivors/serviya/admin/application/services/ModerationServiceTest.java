package com.parosurvivors.serviya.admin.application.services;

import com.parosurvivors.serviya.admin.application.dto.command.RemoveFeedbackCommand;
import com.parosurvivors.serviya.feedback.api.FeedbackCommandApi;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.reports.api.ReportCommandApi;
import com.parosurvivors.serviya.reports.api.ReportQueryApi;
import com.parosurvivors.serviya.reports.api.dto.ReportSummarySnapshot;
import com.parosurvivors.serviya.requests.api.RequestCommandApi;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.users.api.UserCommandApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private ReportQueryApi reportQueryApi;
    @Mock private ReportCommandApi reportCommandApi;
    @Mock private FeedbackCommandApi feedbackCommandApi;
    @Mock private RequestCommandApi requestCommandApi;
    @Mock private UserCommandApi userCommandApi;
    @Mock private NotificationCommandApi notificationCommandApi;
    @Mock private AdminActionGuard adminActionGuard;

    @InjectMocks
    private ModerationService service;

    private static final Long ADMIN = 100L;
    private static final Long REPORTED = 45L;

    private ReportSummarySnapshot serviceFeedbackReport(Long reportId, Long feedbackId) {
        return new ReportSummarySnapshot(reportId, 8L, REPORTED, "SERVICE_FEEDBACK", "SPAM",
                "PENDING", null, feedbackId, null);
    }

    private ReportSummarySnapshot clientFeedbackReport(Long reportId, Long feedbackId) {
        return new ReportSummarySnapshot(reportId, 8L, REPORTED, "CLIENT_FEEDBACK", "SPAM",
                "PENDING", null, null, feedbackId);
    }

    private ReportSummarySnapshot requestReport(Long reportId, Long requestId) {
        return new ReportSummarySnapshot(reportId, 8L, REPORTED, "REQUEST", "SPAM",
                "PENDING", requestId, null, null);
    }

    @Test
    void warnUserNotifiesReportedAndResolvesWithWarn() {
        when(reportQueryApi.findSummary(1L)).thenReturn(Optional.of(requestReport(1L, 99L)));

        service.warnUser(1L, ADMIN);

        verify(notificationCommandApi).notify(any());
        verify(reportCommandApi).resolveReport(1L, ADMIN, "WARN");
        verify(userCommandApi, never()).banUser(any(), any());
    }

    @Test
    void banUserBansAndResolvesWithoutNotifyingItself() {
        when(reportQueryApi.findSummary(2L)).thenReturn(Optional.of(requestReport(2L, 99L)));

        service.banUserFromReport(2L, ADMIN, null);

        // Sin motivo del admin: se banea con un motivo derivado de la categoría del reporte.
        verify(userCommandApi).banUser(eq(REPORTED), anyString());
        verify(reportCommandApi).resolveReport(2L, ADMIN, "BAN");
        // La notificación al baneado la hace UserCommandApi.banUser, no ModerationService.
        verify(notificationCommandApi, never()).notify(any());
    }

    @Test
    void banUserFromReportAppliesSelfActionAndLastAdminGuards() {
        when(reportQueryApi.findSummary(10L)).thenReturn(Optional.of(requestReport(10L, 99L)));

        service.banUserFromReport(10L, ADMIN, "spam");

        // B5: el adminId se usa para evitar auto-ban y dejar al sistema sin administradores.
        verify(adminActionGuard).requireNotSelf(ADMIN, REPORTED);
        verify(adminActionGuard).requireAnotherActiveAdmin(REPORTED);
        verify(userCommandApi).banUser(eq(REPORTED), anyString());
    }

    @Test
    void banUserFromReportAbortsWhenGuardRejects() {
        // Reporte cuyo reportado es el propio admin (auto-accion).
        when(reportQueryApi.findSummary(11L))
                .thenReturn(Optional.of(new ReportSummarySnapshot(11L, 8L, ADMIN, "REQUEST", "SPAM",
                        "PENDING", 99L, null, null)));
        doThrow(new InvalidStateException("self")).when(adminActionGuard).requireNotSelf(ADMIN, ADMIN);

        assertThatThrownBy(() -> service.banUserFromReport(11L, ADMIN, "spam"))
                .isInstanceOf(InvalidStateException.class);
        verify(userCommandApi, never()).banUser(any(), any());
        verify(reportCommandApi, never()).resolveReport(any(), any(), any());
    }

    @Test
    void markRequestAsNotProvidedTransitionsRequestAndResolves() {
        when(reportQueryApi.findSummary(3L)).thenReturn(Optional.of(requestReport(3L, 99L)));

        service.markRequestAsNotProvided(3L, ADMIN);

        verify(requestCommandApi).markAsNotProvided(99L, ADMIN);
        verify(reportCommandApi).resolveReport(3L, ADMIN, "MARK_REQUEST_NOT_PROVIDED");
    }

    @Test
    void markRequestAsNotProvidedFailsWhenNotARequestReport() {
        when(reportQueryApi.findSummary(4L)).thenReturn(Optional.of(serviceFeedbackReport(4L, 77L)));

        assertThatThrownBy(() -> service.markRequestAsNotProvided(4L, ADMIN))
                .isInstanceOf(InvalidStateException.class);
        verify(requestCommandApi, never()).markAsNotProvided(any(), any());
        verify(reportCommandApi, never()).resolveReport(any(), any(), any());
    }

    @Test
    void closeReportDelegatesToFinalize() {
        service.closeReport(5L, ADMIN);
        verify(reportCommandApi).closeReport(5L, ADMIN);
    }

    @Test
    void revertServiceFeedbackResolvesWithRevert() {
        when(reportQueryApi.findSummary(6L)).thenReturn(Optional.of(serviceFeedbackReport(6L, 77L)));
        when(feedbackCommandApi.revertServiceFeedback(77L)).thenReturn(true);

        service.revertFeedbackFromReport(6L, ADMIN);

        verify(feedbackCommandApi).revertServiceFeedback(77L);
        verify(reportCommandApi).resolveReport(6L, ADMIN, "REVERT_FEEDBACK");
    }

    @Test
    void revertClientFeedbackResolvesWithRevert() {
        when(reportQueryApi.findSummary(7L)).thenReturn(Optional.of(clientFeedbackReport(7L, 88L)));
        when(feedbackCommandApi.revertClientFeedback(88L)).thenReturn(true);

        service.revertFeedbackFromReport(7L, ADMIN);

        verify(feedbackCommandApi).revertClientFeedback(88L);
        verify(reportCommandApi).resolveReport(7L, ADMIN, "REVERT_FEEDBACK");
    }

    @Test
    void revertFailsWhenFeedbackAlreadyReverted() {
        when(reportQueryApi.findSummary(8L)).thenReturn(Optional.of(serviceFeedbackReport(8L, 77L)));
        when(feedbackCommandApi.revertServiceFeedback(77L)).thenReturn(false);

        assertThatThrownBy(() -> service.revertFeedbackFromReport(8L, ADMIN))
                .isInstanceOf(InvalidStateException.class);
        verify(reportCommandApi, never()).resolveReport(any(), any(), any());
    }

    @Test
    void removeFeedbackDirectlyCreatesReportThenReverts() {
        RemoveFeedbackCommand command = new RemoveFeedbackCommand(ADMIN, "SERVICE_FEEDBACK", 77L, REPORTED, "SPAM", "ofensivo");
        when(reportCommandApi.createServiceFeedbackReport(ADMIN, REPORTED, "SPAM", "ofensivo", 77L))
                .thenReturn(9L);
        // revertFeedbackFromReport(9L, ...) internamente vuelve a leer el summary.
        when(reportQueryApi.findSummary(9L)).thenReturn(Optional.of(serviceFeedbackReport(9L, 77L)));
        when(feedbackCommandApi.revertServiceFeedback(77L)).thenReturn(true);

        service.removeFeedbackDirectly(command);

        verify(feedbackCommandApi).revertServiceFeedback(77L);
        verify(reportCommandApi).resolveReport(9L, ADMIN, "REVERT_FEEDBACK");
    }

    @Test
    void removeFeedbackDirectlyFailsOnInvalidTargetType() {
        RemoveFeedbackCommand command = new RemoveFeedbackCommand(ADMIN, "FOO", 77L, REPORTED, "SPAM", "x");

        assertThatThrownBy(() -> service.removeFeedbackDirectly(command))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void removeFeedbackDirectlyRejectsNonExactTargetType() {
        // Solo se acepta el nombre exacto del tipo: variantes/subcadenas se rechazan.
        for (String bad : new String[] {"SERVICE", "CLIENT", "SERVICES", "service_feedback", "SERVICE_FEEDBACK "}) {
            assertThatThrownBy(() -> service.removeFeedbackDirectly(
                    new RemoveFeedbackCommand(ADMIN, bad, 77L, REPORTED, "SPAM", "x")))
                    .isInstanceOf(InvalidStateException.class);
        }
    }
}
