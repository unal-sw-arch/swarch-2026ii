package com.parosurvivors.serviya.services.infrastructure.adapters.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.infrastructure.entities.ServiceEntity;
import com.parosurvivors.serviya.services.infrastructure.mappers.ServicePersistenceMapper;
import com.parosurvivors.serviya.services.infrastructure.repositories.ServiceRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ServicePersistenceAdapterTest {

    @Mock private ServiceRepository repository;
    @Mock private ServicePersistenceMapper mapper;

    @InjectMocks private ServicePersistenceAdapter adapter;

    private static final Long SERVICE_ID = 1L;

    private Service domainService() {
        return Service.builder()
                .id(SERVICE_ID)
                .offererId(10L)
                .title("Plomeria")
                .priceHourly(new BigDecimal("50000"))
                .categoryId(3L)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ServiceEntity entity() {
        ServiceEntity e = new ServiceEntity();
        e.setId(SERVICE_ID);
        e.setOffererId(10L);
        e.setTitle("Plomeria");
        e.setPriceHourly(new BigDecimal("50000"));
        e.setCategoryId(3L);
        e.setActive(true);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    @Test
    void saveConvertsAndPersistsEntity() {
        Service domain = domainService();
        ServiceEntity ent = entity();
        ServiceEntity saved = entity();
        saved.setId(5L);

        when(mapper.toEntity(domain)).thenReturn(ent);
        when(repository.save(ent)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);

        Service result = adapter.save(domain);

        ArgumentCaptor<ServiceEntity> captor = ArgumentCaptor.forClass(ServiceEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(ent);
        assertThat(result).isSameAs(domain);
    }

    @Test
    void findByIdReturnsMappedDomain() {
        ServiceEntity ent = entity();
        Service domain = domainService();
        when(repository.findById(SERVICE_ID)).thenReturn(Optional.of(ent));
        when(mapper.toDomain(ent)).thenReturn(domain);

        Optional<Service> result = adapter.findById(SERVICE_ID);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(domain);
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        Optional<Service> result = adapter.findById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllReturnsMappedList() {
        ServiceEntity ent1 = entity();
        ServiceEntity ent2 = entity();
        ent2.setId(2L);
        Service dom1 = domainService();
        Service dom2 = Service.builder().id(2L).title("Electricidad").build();

        when(repository.findAll()).thenReturn(List.of(ent1, ent2));
        when(mapper.toDomain(ent1)).thenReturn(dom1);
        when(mapper.toDomain(ent2)).thenReturn(dom2);

        List<Service> result = adapter.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Plomeria");
        assertThat(result.get(1).getTitle()).isEqualTo("Electricidad");
    }

    @Test
    void findByOffererIdReturnsMappedList() {
        ServiceEntity ent = entity();
        Service domain = domainService();
        when(repository.findByOffererId(10L)).thenReturn(List.of(ent));
        when(mapper.toDomain(ent)).thenReturn(domain);

        List<Service> result = adapter.findByOffererId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOffererId()).isEqualTo(10L);
    }

    @Test
    void deleteByIdDelegatesToRepository() {
        adapter.deleteById(SERVICE_ID);

        verify(repository).deleteById(SERVICE_ID);
    }

    @Test
    void updateSavesAndReturns() {
        Service domain = domainService();
        ServiceEntity ent = entity();
        ServiceEntity saved = entity();
        when(mapper.toEntity(domain)).thenReturn(ent);
        when(repository.save(ent)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(domain);

        Service result = adapter.update(domain);

        verify(repository).save(ent);
        assertThat(result).isSameAs(domain);
    }
}
