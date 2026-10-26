package com.parosurvivors.serviya.metrics.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "service_tag_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uq_service_tag_metric", columnNames = {"tag_id", "service_id"})
)
@Getter
@Setter
public class ServiceTagMetricsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "tag_count", nullable = false)
    private Integer tagCount;
}
