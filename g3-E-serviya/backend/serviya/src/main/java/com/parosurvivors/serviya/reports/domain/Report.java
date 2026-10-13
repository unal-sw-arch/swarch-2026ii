package com.parosurvivors.serviya.reports.domain;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Reporte base. Mapea la tabla {@code reports}, que incluye el discriminador {@code report_type}.
 * Los datos específicos de cada tipo viven en tablas de extensión ({@code request_reports},
 * {@code service_feedback_reports}, {@code client_feedback_reports}) y se modelan por composición
 * en {@link RequestReport}, {@link ServiceFeedbackReport} y {@link ClientFeedbackReport}
 * (ver NOTAS.txt sobre la elección de composición frente a herencia).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {
    private Long id;
    private Long reporterId;
    private Long reportedUserId;
    private ReportType reportType;
    private String category;
    private String reason;
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;
    @Builder.Default
    private ReportPriority priority = ReportPriority.MEDIUM;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }

    public boolean isClosed() {
        return status == ReportStatus.CLOSED;
    }

    public void resolve() {
        if (status == ReportStatus.RESOLVED) {
            return;
        }
        if (isClosed()) {
            throw new InvalidStateException("Un reporte cerrado no puede resolverse");
        }
        this.status = ReportStatus.RESOLVED;
        touch();
    }

    public void close() {
        if (isClosed()) {
            return;
        }
        this.status = ReportStatus.CLOSED;
        touch();
    }

    public void changePriority(ReportPriority newPriority) {
        this.priority = newPriority;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
