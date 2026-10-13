package com.parosurvivors.serviya.reports.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Extensión de un reporte de tipo REQUEST. Mapea la tabla {@code request_reports}
 * (1:1 con un {@link Report} vía {@code reportId}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestReport {
    private Long id;
    private Long reportId;
    private Long requestId;
}
