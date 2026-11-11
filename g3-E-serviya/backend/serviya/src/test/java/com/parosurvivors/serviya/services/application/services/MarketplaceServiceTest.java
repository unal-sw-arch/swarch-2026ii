package com.parosurvivors.serviya.services.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.services.application.dto.command.CreateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.application.mappers.ServiceCommandMapper;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock private ServicePersistencePort persistencePort;
    @Mock private ServiceCommandMapper commandMapper;

    @InjectMocks private MarketplaceService service;

    private static final Long SERVICE_ID = 1L;
    private static final Long OFFERER_ID = 10L;
    private static final Long CATEGORY_ID = 3L;

    private com.parosurvivors.serviya.services.domain.Service sampleService() {
        return com.parosurvivors.serviya.services.domain.Service.builder()
                .id(SERVICE_ID)
                .offererId(OFFERER_ID)
                .title("Plomeriaresidencial")
                .description("Servicio completo")
                .photos(List.of("photo1.jpg"))
                .priceHourly(new BigDecimal("50000"))
                .categoryId(CATEGORY_ID)
                .averageDurationMinutes(60)
                .active(true)
                .operationRadiusKm(new BigDecimal("10.0"))
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    // ==================== create ====================

    @Test
    void createSetsTimestampsAndReturnsService() {
        CreateServiceCommand command = new CreateServiceCommand(
                OFFERER_ID, "Plomeria", "Desc", List.of(), new BigDecimal("50000"), CATEGORY_ID, 60, new BigDecimal("10"));
        com.parosurvivors.serviya.services.domain.Service mapped = sampleService();
        mapped.setCreatedAt(null);
        mapped.setUpdatedAt(null);

        when(commandMapper.toDomain(command)).thenReturn(mapped);
        when(persistencePort.save(any())).thenAnswer(inv -> {
            com.parosurvivors.serviya.services.domain.Service s = inv.getArgument(0);
            s.setId(SERVICE_ID);
            return s;
        });

        com.parosurvivors.serviya.services.domain.Service result = service.create(command);

        ArgumentCaptor<com.parosurvivors.serviya.services.domain.Service> captor =
                ArgumentCaptor.forClass(com.parosurvivors.serviya.services.domain.Service.class);
        verify(persistencePort).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
        assertThat(captor.getValue().getActive()).isTrue();
        assertThat(result.getId()).isEqualTo(SERVICE_ID);
    }

    // ==================== getById ====================

    @Test
    void getByIdReturnsServiceWhenNotDeleted() {
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        Optional<com.parosurvivors.serviya.services.domain.Service> result = service.getById(SERVICE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(SERVICE_ID);
    }

    @Test
    void getByIdReturnsEmptyWhenServiceIsDeleted() {
        com.parosurvivors.serviya.services.domain.Service deleted = sampleService();
        deleted.setDeletedAt(LocalDateTime.now());
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(deleted));

        Optional<com.parosurvivors.serviya.services.domain.Service> result = service.getById(SERVICE_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getByIdReturnsEmptyWhenNotFound() {
        when(persistencePort.findById(99L)).thenReturn(Optional.empty());

        Optional<com.parosurvivors.serviya.services.domain.Service> result = service.getById(99L);

        assertThat(result).isEmpty();
    }

    // ==================== getAll ====================

    @Test
    void getAllFiltersDeletedServices() {
        com.parosurvivors.serviya.services.domain.Service active = sampleService();
        com.parosurvivors.serviya.services.domain.Service deleted = sampleService();
        deleted.setId(2L);
        deleted.setDeletedAt(LocalDateTime.now());

        when(persistencePort.findAll()).thenReturn(List.of(active, deleted));

        List<com.parosurvivors.serviya.services.domain.Service> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(SERVICE_ID);
    }

    // ==================== search ====================

    @Test
    void searchDelegatesToPersistencePort() {
        SearchServiceQuery query = SearchServiceQuery.builder().name("plomeria").build();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<com.parosurvivors.serviya.services.domain.Service> page =
                new org.springframework.data.domain.PageImpl<>(List.of(sampleService()));
        when(persistencePort.search(any(SearchServiceQuery.class), any())).thenReturn(page);

        org.springframework.data.domain.Page<com.parosurvivors.serviya.services.domain.Service> result =
                service.search(query, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(persistencePort).search(query, pageable);
    }

    // ==================== update ====================

    @Test
    void updateAppliesPatchSemanticsAndSetsTimestamp() {
        com.parosurvivors.serviya.services.domain.Service existing = sampleService();
        existing.setUpdatedAt(LocalDateTime.now().minusDays(1));
        UpdateServiceCommand command = new UpdateServiceCommand(
                SERVICE_ID, "Nuevo titulo", null, null, null, null, null, null);

        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(existing));

        service.update(command, OFFERER_ID, false);

        ArgumentCaptor<com.parosurvivors.serviya.services.domain.Service> captor =
                ArgumentCaptor.forClass(com.parosurvivors.serviya.services.domain.Service.class);
        verify(persistencePort).update(captor.capture());
        verify(commandMapper).updateFromCommand(command, existing);
        assertThat(captor.getValue().getUpdatedAt()).isAfterOrEqualTo(LocalDateTime.now().minusSeconds(2));
    }

    @Test
    void updateThrowsWhenServiceNotFound() {
        UpdateServiceCommand command = new UpdateServiceCommand(
                99L, "Title", null, null, null, null, null, null);

        when(persistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(command, OFFERER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Servicio no encontrado");

        verify(persistencePort, never()).update(any());
    }

    // ==================== delete ====================

    @Test
    void deleteRemovesExistingService() {
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        service.delete(SERVICE_ID, OFFERER_ID, false);

        verify(persistencePort).deleteById(SERVICE_ID);
    }

    @Test
    void deleteThrowsWhenServiceNotFound() {
        when(persistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, OFFERER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(persistencePort, never()).deleteById(anyLong());
    }

    // ==================== softDelete ====================

    @Test
    void softDeleteSetsDeletedAtAndActiveFalse() {
        com.parosurvivors.serviya.services.domain.Service svc = sampleService();
        svc.setActive(true);
        svc.setDeletedAt(null);
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(svc));

        service.softDelete(SERVICE_ID, OFFERER_ID, false);

        ArgumentCaptor<com.parosurvivors.serviya.services.domain.Service> captor =
                ArgumentCaptor.forClass(com.parosurvivors.serviya.services.domain.Service.class);
        verify(persistencePort).update(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void softDeleteThrowsWhenNotFound() {
        when(persistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(99L, OFFERER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(persistencePort, never()).update(any());
    }

    // ==================== activate ====================

    @Test
    void activateSetsActiveTrueAndUpdates() {
        com.parosurvivors.serviya.services.domain.Service svc = sampleService();
        svc.setActive(false);
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(svc));

        service.activate(SERVICE_ID, OFFERER_ID, false);

        ArgumentCaptor<com.parosurvivors.serviya.services.domain.Service> captor =
                ArgumentCaptor.forClass(com.parosurvivors.serviya.services.domain.Service.class);
        verify(persistencePort).update(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void activateThrowsWhenNotFound() {
        when(persistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(99L, OFFERER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(persistencePort, never()).update(any());
    }

    // ==================== deactivate ====================

    @Test
    void deactivateSetsActiveFalseAndUpdates() {
        com.parosurvivors.serviya.services.domain.Service svc = sampleService();
        svc.setActive(true);
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(svc));

        service.deactivate(SERVICE_ID, OFFERER_ID, false);

        ArgumentCaptor<com.parosurvivors.serviya.services.domain.Service> captor =
                ArgumentCaptor.forClass(com.parosurvivors.serviya.services.domain.Service.class);
        verify(persistencePort).update(captor.capture());
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void deactivateThrowsWhenNotFound() {
        when(persistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L, OFFERER_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(persistencePort, never()).update(any());
    }

    // ==================== IDOR: ownership (B1) ====================

    private void assertNotOwner(ThrowingCallable assertThrown) {
        assertThatThrownBy(assertThrown)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("propietario");
    }

    private static final Long OTHER_USER_ID = 999L;

    @Test
    void updateThrowsWhenNotOwner() {
        UpdateServiceCommand command = new UpdateServiceCommand(
                SERVICE_ID, "Nuevo titulo", null, null, null, null, null, null);
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        assertNotOwner(() -> service.update(command, OTHER_USER_ID, false));

        verify(persistencePort, never()).update(any());
    }

    @Test
    void deleteThrowsWhenNotOwner() {
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        assertNotOwner(() -> service.delete(SERVICE_ID, OTHER_USER_ID, false));

        verify(persistencePort, never()).deleteById(anyLong());
    }

    @Test
    void deleteAllowsAdmin() {
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        service.delete(SERVICE_ID, OTHER_USER_ID, true);

        verify(persistencePort).deleteById(SERVICE_ID);
    }

    @Test
    void softDeleteThrowsWhenNotOwner() {
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        assertNotOwner(() -> service.softDelete(SERVICE_ID, OTHER_USER_ID, false));

        verify(persistencePort, never()).update(any());
    }

    @Test
    void activateThrowsWhenNotOwner() {
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        assertNotOwner(() -> service.activate(SERVICE_ID, OTHER_USER_ID, false));

        verify(persistencePort, never()).update(any());
    }

    @Test
    void deactivateThrowsWhenNotOwner() {
        when(persistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(sampleService()));

        assertNotOwner(() -> service.deactivate(SERVICE_ID, OTHER_USER_ID, false));

        verify(persistencePort, never()).update(any());
    }

    // ==================== deactivateAllByOfferer ====================

    @Test
    void deactivateAllByOffererSkipsInactiveAndDeleted() {
        com.parosurvivors.serviya.services.domain.Service available = sampleService();
        available.setActive(true);
        available.setDeletedAt(null);

        com.parosurvivors.serviya.services.domain.Service inactive = sampleService();
        inactive.setId(2L);
        inactive.setActive(false);
        inactive.setDeletedAt(null);

        com.parosurvivors.serviya.services.domain.Service deleted = sampleService();
        deleted.setId(3L);
        deleted.setActive(false);
        deleted.setDeletedAt(LocalDateTime.now());

        when(persistencePort.findByOffererId(OFFERER_ID)).thenReturn(List.of(available, inactive, deleted));

        service.deactivateAllByOfferer(OFFERER_ID);

        ArgumentCaptor<com.parosurvivors.serviya.services.domain.Service> captor =
                ArgumentCaptor.forClass(com.parosurvivors.serviya.services.domain.Service.class);
        verify(persistencePort, never()).update(inactive);
        verify(persistencePort, never()).update(deleted);
    }

    @Test
    void deactivateAllByOffererEmptyListDoesNothing() {
        when(persistencePort.findByOffererId(OFFERER_ID)).thenReturn(Collections.emptyList());

        service.deactivateAllByOfferer(OFFERER_ID);

        verify(persistencePort, never()).update(any());
    }
}
