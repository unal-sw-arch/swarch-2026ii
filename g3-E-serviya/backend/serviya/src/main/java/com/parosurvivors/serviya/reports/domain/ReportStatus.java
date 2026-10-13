package com.parosurvivors.serviya.reports.domain;

/**
 * Estado de un reporte. Coincide con el ENUM de la columna {@code reports.status}.
 */
public enum ReportStatus {
    PENDING,
    RESOLVED,
    CLOSED
}
