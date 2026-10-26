package com.parosurvivors.serviya.reports.domain;

/**
 * Tipo de reporte (discriminador). Coincide con el ENUM de la columna {@code reports.report_type}.
 */
public enum ReportType {
    REQUEST,
    SERVICE_FEEDBACK,
    CLIENT_FEEDBACK
}
