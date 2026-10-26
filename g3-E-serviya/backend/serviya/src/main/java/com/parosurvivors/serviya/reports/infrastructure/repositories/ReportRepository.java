package com.parosurvivors.serviya.reports.infrastructure.repositories;

import com.parosurvivors.serviya.reports.domain.ReportStatus;
import com.parosurvivors.serviya.reports.domain.ReportType;
import com.parosurvivors.serviya.reports.infrastructure.entities.ReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {
    List<ReportEntity> findByReporterId(Long reporterId);
    List<ReportEntity> findByReportedUserId(Long reportedUserId);
    List<ReportEntity> findByStatus(ReportStatus status);
    List<ReportEntity> findByReportType(ReportType reportType);
    long countByReporterId(Long reporterId);
    long countByReportedUserId(Long reportedUserId);

    Page<ReportEntity> findByReportTypeAndCategoryAndStatus(ReportType reportType, String category, ReportStatus status, Pageable pageable);

    Page<ReportEntity> findByReportTypeAndCategory(ReportType reportType, String category, Pageable pageable);

    Page<ReportEntity> findByReportTypeAndStatus(ReportType reportType, ReportStatus status, Pageable pageable);

    Page<ReportEntity> findByCategoryAndStatus(String category, ReportStatus status, Pageable pageable);

    Page<ReportEntity> findByReportType(ReportType reportType, Pageable pageable);

    Page<ReportEntity> findByCategory(String category, Pageable pageable);

    Page<ReportEntity> findByStatus(ReportStatus status, Pageable pageable);

    @Query("SELECT r FROM ReportEntity r")
    Page<ReportEntity> findAllReports(Pageable pageable);
}
