package com.parosurvivors.serviya.services.infrastructure.adapters.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.services.domain.ServiceAvailability;
import com.parosurvivors.serviya.services.infrastructure.entities.ServiceAvailabilityEntity;
import com.parosurvivors.serviya.services.infrastructure.mappers.ServiceAvailabilityPersistenceMapper;
import com.parosurvivors.serviya.services.infrastructure.repositories.ServiceAvailabilityRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ServiceAvailabilityPersistenceAdapterTest {

    @Mock private ServiceAvailabilityRepository repository;
    @Mock private ServiceAvailabilityPersistenceMapper mapper;

    @InjectMocks private ServiceAvailabilityPersistenceAdapter adapter;

    private static final Long AVAILABILITY_ID = 1L;
    private static final long SERVICE_ID = 10L;

    private ServiceAvailability domainAvailability() {
        return ServiceAvailability.builder()
                .id(AVAILABILITY_ID)
                .serviceId(SERVICE_ID)
                .weekDay((byte) 1)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .isActive(true)
                .build();
    }

    private ServiceAvailabilityEntity entityAvailability() {
        ServiceAvailabilityEntity e = new ServiceAvailabilityEntity();
        e.setId(AVAILABILITY_ID);
        e.setServiceId(SERVICE_ID);
        e.setWeekDay((byte) 1);
        e.setStartTime(LocalTime.of(8, 0));
        e.setEndTime(LocalTime.of(17, 0));
        e.setActive(true);
        return e;
    }

    @Test
    void saveConvertsAndPersists() {
        ServiceAvailability domain = domainAvailability();
        ServiceAvailabilityEntity ent = entityAvailability();
        ServiceAvailabilityEntity saved = entityAvailability();
        saved.setId(5L);

        when(mapper.toEntity(domain)).thenReturn(ent);
        when(repository.save(ent)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);

        ServiceAvailability result = adapter.save(domain);

        ArgumentCaptor<ServiceAvailabilityEntity> captor = ArgumentCaptor.forClass(ServiceAvailabilityEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(ent);
        assertThat(result).isSameAs(domain);
    }

    @Test
    void deleteByIdDelegatesToRepository() {
        adapter.deleteById(AVAILABILITY_ID);

        verify(repository).deleteById(AVAILABILITY_ID);
    }

    @Test
    void updateSavesAndReturns() {
        ServiceAvailability domain = domainAvailability();
        ServiceAvailabilityEntity ent = entityAvailability();
        ServiceAvailabilityEntity saved = entityAvailability();

        when(mapper.toEntity(domain)).thenReturn(ent);
        when(repository.save(ent)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);

        ServiceAvailability result = adapter.update(domain);

        verify(repository).save(ent);
        assertThat(result).isSameAs(domain);
    }

    @Test
    void findByServiceIdReturnsMappedList() {
        ServiceAvailabilityEntity ent1 = entityAvailability();
        ServiceAvailabilityEntity ent2 = entityAvailability();
        ent2.setId(2L);
        ServiceAvailability dom1 = domainAvailability();
        ServiceAvailability dom2 = ServiceAvailability.builder()
                .id(2L).serviceId(SERVICE_ID).weekDay((byte) 3).isActive(true).build();

        when(repository.findByServiceId(SERVICE_ID)).thenReturn(List.of(ent1, ent2));
        when(mapper.toDomain(ent1)).thenReturn(dom1);
        when(mapper.toDomain(ent2)).thenReturn(dom2);

        List<ServiceAvailability> result = adapter.findByServiceId(SERVICE_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getWeekDay()).isEqualTo((byte) 1);
        assertThat(result.get(1).getWeekDay()).isEqualTo((byte) 3);
    }

    @Test
    void findByIdReturnsMappedAvailability() {
        ServiceAvailabilityEntity ent = entityAvailability();
        ServiceAvailability domain = domainAvailability();
        when(repository.findById(AVAILABILITY_ID)).thenReturn(Optional.of(ent));
        when(mapper.toDomain(ent)).thenReturn(domain);

        ServiceAvailability result = adapter.findById(AVAILABILITY_ID);

        assertThat(result).isSameAs(domain);
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Disponibilidad no encontrada");
    }
}
