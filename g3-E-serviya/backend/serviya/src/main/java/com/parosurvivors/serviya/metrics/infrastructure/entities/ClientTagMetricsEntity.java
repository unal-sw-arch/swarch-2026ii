package com.parosurvivors.serviya.metrics.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "client_tag_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uq_client_tag_metric", columnNames = {"client_id", "tag_id"})
)
@Getter
@Setter
public class ClientTagMetricsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "tag_count", nullable = false)
    private Integer tagCount;
}
