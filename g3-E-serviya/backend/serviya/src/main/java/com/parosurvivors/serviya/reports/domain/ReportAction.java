package com.parosurvivors.serviya.reports.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Acción administrativa registrada sobre un reporte (trazabilidad). Mapea la tabla
 * {@code report_actions}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAction {
    private Long id;
    private Long reportId;
    private Long adminId;
    /** Discriminador máquina-legible de la acción; determina la plantilla de {@code actionDescription}. */
    private ReportActionType actionType;
    private String actionDescription;
    private LocalDateTime createdAt;
}
