package com.parosurvivors.serviya.services.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Service {
    private Long id;
    private Long offererId;
    private String title;
    private String description;
    private List<String> photos;
    private BigDecimal priceHourly;
    private Long categoryId;
    private Integer averageDurationMinutes;
    private Boolean active;
    private BigDecimal operationRadiusKm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public void activate() {
        this.active = true;
    }
    public void deactivate() {
        this.active = false;
    }
    public boolean isDeleted() {
        return deletedAt != null;
    }
    public boolean isAvailable() {
        return Boolean.TRUE.equals(active)
                && deletedAt == null;
    }
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.active = false;
    }
}
