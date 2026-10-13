package com.parosurvivors.serviya.reports.domain;

/**
 * Tipo de acción administrativa registrada sobre un reporte. Cada valor corresponde 1:1 con el
 * método de {@code ModerationService} que la produce, y es el discriminador máquina-legible de la
 * tabla {@code report_actions} (columna {@code action_type}). Su método {@link #describe} genera la
 * descripción humana preestablecida que se persiste como snapshot inmutable de trazabilidad.
 */
public enum ReportActionType {
    WARN,
    BAN,
    REVERT_FEEDBACK,
    MARK_REQUEST_NOT_PROVIDED,
    CLOSE;

    /**
     * Construye la descripción preestablecida de la acción, embebiendo los hechos del reporte
     * (a quién afectó, quién reportó, reporte origen, categoría) para que el registro sea
     * autocontenido. El "cuándo" y el "quién ejecutó" viven en columnas propias del registro
     * ({@code created_at}, {@code admin_id}), por lo que no se repiten aquí salvo el admin, que
     * hace la frase legible sin un JOIN.
     */
    public String describe(Report report, Long adminId) {
        Long reportedUserId = report.getReportedUserId();
        Long reporterId = report.getReporterId();
        Long reportId = report.getId();
        String category = report.getCategory();
        return switch (this) {
            case WARN -> "[WARN] El administrador #%d advirtió al usuario reportado #%d (reportado por #%d) por el reporte #%d, categoría \"%s\"."
                    .formatted(adminId, reportedUserId, reporterId, reportId, category);
            case BAN -> "[BAN] El administrador #%d baneó al usuario reportado #%d (reportado por #%d) a raíz del reporte #%d, categoría \"%s\"."
                    .formatted(adminId, reportedUserId, reporterId, reportId, category);
            case REVERT_FEEDBACK -> "[REVERT_FEEDBACK] El administrador #%d revirtió el feedback objeto del reporte #%d contra el usuario reportado #%d (reportado por #%d), categoría \"%s\"."
                    .formatted(adminId, reportId, reportedUserId, reporterId, category);
            case MARK_REQUEST_NOT_PROVIDED -> "[MARK_REQUEST_NOT_PROVIDED] El administrador #%d marcó como no prestada la solicitud del reporte #%d contra el usuario reportado #%d (reportado por #%d), categoría \"%s\"."
                    .formatted(adminId, reportId, reportedUserId, reporterId, category);
            case CLOSE -> "[CLOSE] El administrador #%d cerró sin sanción el reporte #%d contra el usuario reportado #%d (reportado por #%d), categoría \"%s\"."
                    .formatted(adminId, reportId, reportedUserId, reporterId, category);
        };
    }
}
