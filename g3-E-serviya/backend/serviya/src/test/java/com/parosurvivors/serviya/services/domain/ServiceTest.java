package com.parosurvivors.serviya.services.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceTest {

    private Service existingService() {
        return Service.builder()
                .id(1L)
                .offererId(10L)
                .title("Plomeriaresidencial")
                .description("Servicio completo de plomeria")
                .photos(List.of("photo1.jpg"))
                .priceHourly(new BigDecimal("50000"))
                .categoryId(3L)
                .averageDurationMinutes(60)
                .active(true)
                .operationRadiusKm(new BigDecimal("10.0"))
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Test
    void activateSetsActiveTrue() {
        Service service = existingService();
        service.setActive(false);

        service.activate();

        assertThat(service.getActive()).isTrue();
    }

    @Test
    void deactivateSetsActiveFalse() {
        Service service = existingService();
        service.setActive(true);

        service.deactivate();

        assertThat(service.getActive()).isFalse();
    }

    @Test
    void isDeletedTrueWhenDeletedAtNotNull() {
        Service service = existingService();
        service.setDeletedAt(LocalDateTime.now());

        assertThat(service.isDeleted()).isTrue();
    }

    @Test
    void isDeletedFalseWhenDeletedAtNull() {
        Service service = existingService();
        service.setDeletedAt(null);

        assertThat(service.isDeleted()).isFalse();
    }

    @Test
    void isAvailableTrueWhenActiveAndNotDeleted() {
        Service service = existingService();
        service.setActive(true);
        service.setDeletedAt(null);

        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void isAvailableFalseWhenInactive() {
        Service service = existingService();
        service.setActive(false);
        service.setDeletedAt(null);

        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailableFalseWhenDeleted() {
        Service service = existingService();
        service.setActive(true);
        service.setDeletedAt(LocalDateTime.now());

        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void softDeleteSetsDeletedAtAndDeactivates() {
        Service service = existingService();
        service.setActive(true);
        service.setDeletedAt(null);

        service.softDelete();

        assertThat(service.isDeleted()).isTrue();
        assertThat(service.getActive()).isFalse();
        assertThat(service.getDeletedAt()).isNotNull();
    }
}
