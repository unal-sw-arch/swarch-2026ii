package com.parosurvivors.serviya.reports.infrastructure.entities;

import com.parosurvivors.serviya.reports.domain.ReportActionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_actions")
@Getter
@Setter
public class ReportActionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ReportActionType actionType;

    @Column(name = "action_description", nullable = false, columnDefinition = "TEXT")
    private String actionDescription;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
