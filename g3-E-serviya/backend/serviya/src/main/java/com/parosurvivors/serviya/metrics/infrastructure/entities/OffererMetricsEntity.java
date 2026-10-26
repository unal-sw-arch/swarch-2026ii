package com.parosurvivors.serviya.metrics.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "offerer_metrics")
@Getter
@Setter
public class OffererMetricsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "offerer_id", nullable = false, unique = true)
    private Long offererId;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "total_ratings", nullable = false)
    private Integer totalRatings;

    @Column(name = "total_comments", nullable = false)
    private Integer totalComments;

    @Column(name = "total_positive_tags", nullable = false)
    private Integer totalPositiveTags;

    @Column(name = "total_negative_tags", nullable = false)
    private Integer totalNegativeTags;

    @Column(name = "total_requests_received", nullable = false)
    private Integer totalRequestsReceived;

    @Column(name = "total_accepted_requests", nullable = false)
    private Integer totalAcceptedRequests;

    @Column(name = "total_completed_services", nullable = false)
    private Integer totalCompletedServices;

    @Column(name = "total_cancelled_services", nullable = false)
    private Integer totalCancelledServices;

    @Column(name = "total_reschedule_proposals_sent", nullable = false)
    private Integer totalRescheduleProposalsSent;

    @Column(name = "total_not_provided_services", nullable = false)
    private Integer totalNotProvidedServices;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
