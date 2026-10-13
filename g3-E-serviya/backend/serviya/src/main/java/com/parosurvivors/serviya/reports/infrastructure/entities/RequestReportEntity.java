package com.parosurvivors.serviya.reports.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "request_reports")
@Getter
@Setter
public class RequestReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "report_id", nullable = false, unique = true)
    private Long reportId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;
}
