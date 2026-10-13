package com.parosurvivors.serviya.reports.infrastructure.repositories;

import com.parosurvivors.serviya.reports.infrastructure.entities.ReportActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportActionRepository extends JpaRepository<ReportActionEntity, Long> {
    List<ReportActionEntity> findByReportId(Long reportId);
}
