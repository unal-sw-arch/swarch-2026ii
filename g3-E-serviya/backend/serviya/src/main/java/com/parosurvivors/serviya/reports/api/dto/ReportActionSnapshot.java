package com.parosurvivors.serviya.reports.api.dto;

import java.time.LocalDateTime;

/**
 * Accion administrativa registrada sobre un reporte.
 */
public record ReportActionSnapshot(
        Long id,
        Long reportId,
        Long adminId,
        String actionType,
        String actionDescription,
        LocalDateTime createdAt) {
}
