package com.parosurvivors.serviya.services.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "services")
@Getter
@Setter
public class ServiceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "offerer_id", nullable = false)
    private Long offererId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> photos;

    @Column(name = "price_hourly", nullable = false)
    private BigDecimal priceHourly;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "average_duration_minutes")
    private Integer averageDurationMinutes;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "operation_radius_km")
    private BigDecimal operationRadiusKm;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
