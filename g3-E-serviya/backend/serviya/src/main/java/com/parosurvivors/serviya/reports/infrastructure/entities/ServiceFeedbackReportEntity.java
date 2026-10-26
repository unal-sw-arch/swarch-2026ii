package com.parosurvivors.serviya.reports.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "service_feedback_reports")
@Getter
@Setter
public class ServiceFeedbackReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "report_id", nullable = false, unique = true)
    private Long reportId;

    // Nullable: al revertir el feedback, la FK (ON DELETE SET NULL) lo pone en null; el reporte sobrevive.
    @Column(name = "feedback_id")
    private Long feedbackId;
}
