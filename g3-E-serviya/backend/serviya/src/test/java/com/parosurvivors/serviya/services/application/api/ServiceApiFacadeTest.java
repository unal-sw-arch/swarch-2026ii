package com.parosurvivors.serviya.services.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.services.api.dto.CategorySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceAvailabilitySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.application.ports.output.ServiceAvailabilityPersistencePort;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceApiFacadeTest {

    @Mock
    private ServicePersistencePort servicePersistencePort;
    @Mock
    private CategoryPersistencePort categoryPersistencePort;
    @Mock
    private ServiceAvailabilityPersistencePort serviceAvailabilityPersistencePort;
    @Mock
    private MarketplaceServicePort marketplaceServicePort;

    @InjectMocks
    private ServiceApiFacade facade;

    @Test
    void findByIdMapsAllServiceFields() {
        Service service = Service.builder()
                .id(1L)
                .offererId(7L)
                .title("Plumbing")
                .description("Fix pipes")
                .photos(List.of("a.png", "b.png"))
                .priceHourly(new BigDecimal("25.00"))
                .categoryId(3L)
                .averageDurationMinutes(60)
                .active(true)
                .operationRadiusKm(new BigDecimal("10"))
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .updatedAt(LocalDateTime.of(2026, 1, 3, 4, 5))
                .build();
        when(servicePersistencePort.findById(1L)).thenReturn(Optional.of(service));

        ServiceSnapshot snapshot = facade.findById(1L).orElseThrow();

        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.offererId()).isEqualTo(7L);
        assertThat(snapshot.photos()).containsExactly("a.png", "b.png");
        assertThat(snapshot.priceHourly()).isEqualByComparingTo("25.00");
        assertThat(snapshot.active()).isTrue();
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        when(servicePersistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThat(facade.findById(99L)).isEmpty();
    }

    @Test
    void findByIdsMapsBatch() {
        Service first = Service.builder().id(1L).title("A").active(true).build();
        Service second = Service.builder().id(2L).title("B").active(false).build();
        when(servicePersistencePort.findByIds(Set.of(1L, 2L))).thenReturn(List.of(first, second));

        List<ServiceSnapshot> result = facade.findByIds(Set.of(1L, 2L));

        assertThat(result).extracting(ServiceSnapshot::id).containsExactly(1L, 2L);
        assertThat(result.get(1).active()).isFalse();
    }

    @Test
    void findByOffererIdReturnsAllOwnedServices() {
        Service active = Service.builder().id(1L).offererId(7L).active(true).build();
        Service inactive = Service.builder().id(2L).offererId(7L).active(false).build();
        when(servicePersistencePort.findByOffererId(7L)).thenReturn(List.of(active, inactive));

        List<ServiceSnapshot> result = facade.findByOffererId(7L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ServiceSnapshot::active).containsExactly(true, false);
    }

    @Test
    void findCategoryAndGetCategoriesMap() {
        Category category = Category.builder().id(3L).name("Hogar").build();
        when(categoryPersistencePort.findById(3L)).thenReturn(Optional.of(category));
        when(categoryPersistencePort.findAll()).thenReturn(List.of(category));

        CategorySnapshot one = facade.findCategory(3L).orElseThrow();
        List<CategorySnapshot> all = facade.getCategories();

        assertThat(one.name()).isEqualTo("Hogar");
        assertThat(all).extracting(CategorySnapshot::id).containsExactly(3L);
    }

    @Test
    void getAvailabilitiesPreservesServicesWeekDayConvention() {
        ServiceAvailability availability = ServiceAvailability.builder()
                .id(1L).serviceId(2L).weekDay((byte) 1)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).isActive(true)
                .build();
        when(serviceAvailabilityPersistencePort.findByServiceId(2L))
                .thenReturn(List.of(availability));

        List<ServiceAvailabilitySnapshot> result = facade.getAvailabilities(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).weekDay()).isEqualTo(1);
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    void deactivateAllByOffererDelegates() {
        facade.deactivateAllByOfferer(7L);

        verify(marketplaceServicePort).deactivateAllByOfferer(7L);
    }

    @Test
    void deleteServiceAsAdminDelegatesWithAdminFlag() {
        facade.deleteServiceAsAdmin(1L, 9L);

        verify(marketplaceServicePort).delete(1L, 9L, true);
    }
}
