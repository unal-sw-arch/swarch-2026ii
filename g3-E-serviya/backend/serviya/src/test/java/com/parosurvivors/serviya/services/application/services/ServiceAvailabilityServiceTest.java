package com.parosurvivors.serviya.services.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.profiles.application.ports.input.OffererAvailabilityServicePort;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import com.parosurvivors.serviya.services.application.dto.command.CreateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.application.ports.output.ServiceAvailabilityPersistencePort;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ServiceAvailabilityServiceTest {

    @Mock private ServiceAvailabilityPersistencePort persistencePort;
    @Mock private OffererAvailabilityServicePort offererAvailabilityService;

    @InjectMocks private ServiceAvailabilityService service;

    private static final Long SERVICE_ID = 1L;
    private static final Long OFFERER_ID = 10L;
    private static final Long AVAILABILITY_ID = 5L;

    // ==================== create ====================

    @Test
    void createBuildsAvailabilityAndSaves() {
        CreateServiceAvailabilityCommand command = new CreateServiceAvailabilityCommand(
                SERVICE_ID, (byte) 1, LocalTime.of(8, 0), LocalTime.of(17, 0), true);

        when(persistencePort.save(any())).thenAnswer(inv -> {
            ServiceAvailability a = inv.getArgument(0);
            a.setId(AVAILABILITY_ID);
            return a;
        });

        ServiceAvailability result = service.create(command);

        ArgumentCaptor<ServiceAvailability> captor = ArgumentCaptor.forClass(ServiceAvailability.class);
        verify(persistencePort).save(captor.capture());
        assertThat(captor.getValue().getServiceId()).isEqualTo(SERVICE_ID);
        assertThat(captor.getValue().getWeekDay()).isEqualTo((byte) 1);
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(captor.getValue().getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(result.getId()).isEqualTo(AVAILABILITY_ID);
    }

    @Test
    void createThrowsWhenWeekDayOutOfRange() {
        CreateServiceAvailabilityCommand command = new CreateServiceAvailabilityCommand(
                SERVICE_ID, (byte) 7, LocalTime.of(8, 0), LocalTime.of(17, 0), true);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekDay");

        verify(persistencePort, never()).save(any());
    }

    @Test
    void createThrowsWhenTimeRangeInvalid() {
        CreateServiceAvailabilityCommand command = new CreateServiceAvailabilityCommand(
                SERVICE_ID, (byte) 1, LocalTime.of(17, 0), LocalTime.of(8, 0), true);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rango");

        verify(persistencePort, never()).save(any());
    }

    @Test
    void createThrowsWhenServiceIdNull() {
        CreateServiceAvailabilityCommand command = new CreateServiceAvailabilityCommand(
                null, (byte) 1, LocalTime.of(8, 0), LocalTime.of(17, 0), true);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceId");

        verify(persistencePort, never()).save(any());
    }

    // ==================== delete ====================

    @Test
    void deleteCallsPersistenceDeleteById() {
        service.delete(AVAILABILITY_ID);

        verify(persistencePort).deleteById(AVAILABILITY_ID);
    }

    // ==================== update ====================

    @Test
    void updateModifiesExistingAvailability() {
        ServiceAvailability existing = ServiceAvailability.builder()
                .id(AVAILABILITY_ID)
                .serviceId(SERVICE_ID)
                .weekDay((byte) 1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .isActive(true)
                .build();

        UpdateServiceAvailabilityCommand command = new UpdateServiceAvailabilityCommand(
                AVAILABILITY_ID, (byte) 2, LocalTime.of(9, 0), LocalTime.of(18, 0), false);

        when(persistencePort.findById(AVAILABILITY_ID)).thenReturn(existing);
        when(persistencePort.update(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceAvailability result = service.update(command);

        ArgumentCaptor<ServiceAvailability> captor = ArgumentCaptor.forClass(ServiceAvailability.class);
        verify(persistencePort).update(captor.capture());
        assertThat(captor.getValue().getWeekDay()).isEqualTo((byte) 2);
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(captor.getValue().getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void updateThrowsWhenNotFound() {
        UpdateServiceAvailabilityCommand command = new UpdateServiceAvailabilityCommand(
                99L, (byte) 1, LocalTime.of(8, 0), LocalTime.of(17, 0), true);

        when(persistencePort.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Disponibilidad no encontrada");

        verify(persistencePort, never()).update(any());
    }

    @Test
    void updateThrowsWhenWeekDayOutOfRange() {
        ServiceAvailability existing = ServiceAvailability.builder().id(AVAILABILITY_ID).build();
        UpdateServiceAvailabilityCommand command = new UpdateServiceAvailabilityCommand(
                AVAILABILITY_ID, (byte) 8, LocalTime.of(8, 0), LocalTime.of(17, 0), true);

        when(persistencePort.findById(AVAILABILITY_ID)).thenReturn(existing);

        assertThatThrownBy(() -> service.update(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekDay");

        verify(persistencePort, never()).update(any());
    }

    // ==================== getByServiceId ====================

    @Test
    void getByServiceIdReturnsOnlyActiveSlots() {
        ServiceAvailability active1 = ServiceAvailability.builder()
                .id(1L).serviceId(SERVICE_ID).isActive(true).build();
        ServiceAvailability active2 = ServiceAvailability.builder()
                .id(2L).serviceId(SERVICE_ID).isActive(true).build();
        ServiceAvailability inactive = ServiceAvailability.builder()
                .id(3L).serviceId(SERVICE_ID).isActive(false).build();

        when(persistencePort.findByServiceId(SERVICE_ID)).thenReturn(List.of(active1, active2, inactive));

        List<ServiceAvailability> result = service.getByServiceId(SERVICE_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ServiceAvailability::getId).containsExactly(1L, 2L);
    }

    @Test
    void getByServiceIdReturnsEmptyWhenNoneActive() {
        ServiceAvailability inactive = ServiceAvailability.builder()
                .id(1L).serviceId(SERVICE_ID).isActive(false).build();

        when(persistencePort.findByServiceId(SERVICE_ID)).thenReturn(List.of(inactive));

        List<ServiceAvailability> result = service.getByServiceId(SERVICE_ID);

        assertThat(result).isEmpty();
    }

    // ==================== applyGeneralTemplate ====================

    @Test
    void applyGeneralTemplateDeletesExistingAndCreatesFromOfferer() {
        ServiceAvailability existing1 = ServiceAvailability.builder().id(1L).serviceId(SERVICE_ID).build();
        ServiceAvailability existing2 = ServiceAvailability.builder().id(2L).serviceId(SERVICE_ID).build();
        when(persistencePort.findByServiceId(SERVICE_ID)).thenReturn(List.of(existing1, existing2));

        OffererAvailability slot1 = OffererAvailability.builder()
                .weekDay(1).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).active(true).build();
        OffererAvailability slot2 = OffererAvailability.builder()
                .weekDay(3).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).active(true).build();
        OffererAvailability inactiveSlot = OffererAvailability.builder()
                .weekDay(5).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(20, 0)).active(false).build();
        when(offererAvailabilityService.getSchedule(OFFERER_ID)).thenReturn(List.of(slot1, slot2, inactiveSlot));

        when(persistencePort.save(any())).thenAnswer(inv -> {
            ServiceAvailability a = inv.getArgument(0);
            a.setId(System.nanoTime());
            return a;
        });

        service.applyGeneralTemplate(SERVICE_ID, OFFERER_ID);

        verify(persistencePort).deleteById(1L);
        verify(persistencePort).deleteById(2L);
        ArgumentCaptor<ServiceAvailability> captor = ArgumentCaptor.forClass(ServiceAvailability.class);
        verify(persistencePort, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(0).getWeekDay()).isEqualTo((byte) 1);
        assertThat(captor.getAllValues().get(1).getWeekDay()).isEqualTo((byte) 3);
    }
}
