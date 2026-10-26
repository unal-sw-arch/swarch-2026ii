package com.parosurvivors.serviya.metrics.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "offerer_tag_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uq_offerer_tag_metric", columnNames = {"offerer_id", "tag_id"})
)
@Getter
@Setter
public class OffererTagMetricsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "offerer_id", nullable = false)
    private Long offererId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(name = "tag_count", nullable = false)
    private Integer tagCount;
}
