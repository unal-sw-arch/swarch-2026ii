package com.parosurvivors.serviya.services.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ServiceAvailabilityTest {

    @Test
    void checkServiceWeekDayAcceptsValidRange() {
        for (int day = 1; day <= 7; day++) {
            ServiceAvailability.checkServiceWeekDay(day);
        }

        // No exception thrown for 1-7
        assertThat(true).isTrue();
    }

    @Test
    void checkServiceWeekDayThrowsOnInvalidLow() {
        assertThatThrownBy(() -> ServiceAvailability.checkServiceWeekDay(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void checkServiceWeekDayThrowsOnInvalidHigh() {
        assertThatThrownBy(() -> ServiceAvailability.checkServiceWeekDay(8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
    }
}
