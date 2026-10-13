package com.parosurvivors.serviya.metrics.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_metrics")
@Getter
@Setter
public class ServiceMetricsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "service_id", nullable = false, unique = true)
    private Long serviceId;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "total_ratings", nullable = false)
    private Integer totalRatings;

    @Column(name = "total_comments", nullable = false)
    private Integer totalComments;

    @Column(name = "total_requests_received", nullable = false)
    private Integer totalRequestsReceived;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
