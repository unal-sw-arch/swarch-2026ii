package com.parosurvivors.serviya.reports.api;

import com.parosurvivors.serviya.reports.api.dto.ReportActionSnapshot;
import com.parosurvivors.serviya.reports.api.dto.ReportSnapshot;
import com.parosurvivors.serviya.reports.api.dto.ReportSummarySnapshot;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.util.List;
import java.util.Optional;

/**
 * Consultas publicas del modulo reports.
 */
public interface ReportQueryApi {

    Optional<ReportSnapshot> findById(Long reportId);

    Optional<ReportSummarySnapshot> findSummary(Long reportId);

    PageResult<ReportSnapshot> search(String type, String category, String status, int page, int size);

    List<ReportActionSnapshot> getActions(Long reportId);

    long countByReportedUser(Long userId);

    long countByReporter(Long userId);
}
