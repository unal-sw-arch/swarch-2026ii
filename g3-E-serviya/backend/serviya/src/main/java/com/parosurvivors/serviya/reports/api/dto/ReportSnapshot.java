package com.parosurvivors.serviya.reports.api.dto;

import java.time.LocalDateTime;

/**
 * Vista publica base de un reporte.
 */
public record ReportSnapshot(
        Long id,
        Long reporterId,
        Long reportedUserId,
        String reportType,
        String category,
        String reason,
        String status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
