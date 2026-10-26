package com.parosurvivors.serviya.metrics.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_metrics")
@Getter
@Setter
public class ClientMetricsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "client_id", nullable = false, unique = true)
    private Long clientId;

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

    @Column(name = "total_requests_sent", nullable = false)
    private Integer totalRequestsSent;

    @Column(name = "total_accepted_requests", nullable = false)
    private Integer totalAcceptedRequests;

    @Column(name = "total_completed_requests", nullable = false)
    private Integer totalCompletedRequests;

    @Column(name = "total_cancelled_requests", nullable = false)
    private Integer totalCancelledRequests;

    @Column(name = "total_rescheduled_requests", nullable = false)
    private Integer totalRescheduledRequests;

    @Column(name = "total_not_provided_requests", nullable = false)
    private Integer totalNotProvidedRequests;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
