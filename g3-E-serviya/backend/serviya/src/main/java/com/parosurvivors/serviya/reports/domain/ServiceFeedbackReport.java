package com.parosurvivors.serviya.reports.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Extensión de un reporte de tipo SERVICE_FEEDBACK. Mapea la tabla {@code service_feedback_reports}
 * (1:1 con un {@link Report} vía {@code reportId}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceFeedbackReport {
    private Long id;
    private Long reportId;
    private Long feedbackId;
}
