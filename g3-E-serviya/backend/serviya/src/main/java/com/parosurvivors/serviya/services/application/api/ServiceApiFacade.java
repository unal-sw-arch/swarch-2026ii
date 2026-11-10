package com.parosurvivors.serviya.services.application.api;

import com.parosurvivors.serviya.services.api.ServiceCommandApi;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.AvailabilityTemplateSlot;
import com.parosurvivors.serviya.services.api.dto.CategorySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceAvailabilitySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSearchCriteria;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.services.application.dto.command.CreateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.application.ports.output.ServiceAvailabilityPersistencePort;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementacion de los contratos publicos de services sobre los casos de uso existentes.
 *
 * <p>Las lecturas usan los puertos de persistencia del propio modulo. Las vistas compuestas
 * (detalle, busqueda enriquecida, vitrina) viven en el BFF. Las escrituras delegan en
 * {@link MarketplaceServicePort}.</p>
 */
@Component
@RequiredArgsConstructor
public class ServiceApiFacade implements ServiceQueryApi, ServiceCommandApi {

    private final ServicePersistencePort servicePersistencePort;
    private final CategoryPersistencePort categoryPersistencePort;
    private final ServiceAvailabilityPersistencePort serviceAvailabilityPersistencePort;
    private final com.parosurvivors.serviya.services.application.ports.input.ServiceAvailabilityServicePort serviceAvailabilityServicePort;
    private final MarketplaceServicePort marketplaceServicePort;

    // ---------------------------------------------------------------------
    // ServiceQueryApi
    // ---------------------------------------------------------------------

    @Override
    public Optional<ServiceSnapshot> findById(Long serviceId) {
        return servicePersistencePort.findById(serviceId).map(this::toServiceSnapshot);
    }

    @Override
    public List<ServiceSnapshot> findByIds(Collection<Long> serviceIds) {
        return servicePersistencePort.findByIds(serviceIds).stream()
                .map(this::toServiceSnapshot)
                .toList();
    }

    @Override
    public List<ServiceSnapshot> findByOffererId(Long offererId) {
        return servicePersistencePort.findByOffererId(offererId).stream()
                .map(this::toServiceSnapshot)
                .toList();
    }

    @Override
    public Optional<CategorySnapshot> findCategory(Long categoryId) {
        return categoryPersistencePort.findById(categoryId).map(this::toCategorySnapshot);
    }

    @Override
    public List<CategorySnapshot> getCategories() {
        return categoryPersistencePort.findAll().stream()
                .map(this::toCategorySnapshot)
                .toList();
    }

    @Override
    public List<ServiceAvailabilitySnapshot> getAvailabilities(Long serviceId) {
        return serviceAvailabilityPersistencePort.findByServiceId(serviceId).stream()
                .map(this::toAvailabilitySnapshot)
                .toList();
    }

    @Override
    public PageResult<ServiceSnapshot> search(ServiceSearchCriteria criteria, int page, int size) {
        SearchServiceQuery query = new SearchServiceQuery(
                criteria.name(),
                criteria.categoryId(),
                criteria.offererId(),
                criteria.minPrice(),
                criteria.maxPrice(),
                criteria.available(),
                criteria.minRating(),
                criteria.maxRating(),
                criteria.latitude(),
                criteria.longitude(),
                criteria.maxDistanceKm(),
                criteria.serviceIds(),
                criteria.offererIdsByName(),
                criteria.offererIdsWithinDistance());
        Page<Service> result = marketplaceServicePort.search(query, PageRequest.of(page, size));
        return new PageResult<>(
                result.getContent().stream().map(this::toServiceSnapshot).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    // ---------------------------------------------------------------------
    // ServiceCommandApi
    // ---------------------------------------------------------------------

    @Override
    public void deactivateAllByOfferer(Long offererId) {
        marketplaceServicePort.deactivateAllByOfferer(offererId);
    }

    @Override
    public void deleteServiceAsAdmin(Long serviceId, Long adminId) {
        marketplaceServicePort.delete(serviceId, adminId, true);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void applyAvailabilityTemplate(Long serviceId, List<AvailabilityTemplateSlot> slots) {
        serviceAvailabilityPersistencePort.findByServiceId(serviceId)
                .forEach(slot -> serviceAvailabilityPersistencePort.deleteById(slot.getId()));
        if (slots == null) {
            return;
        }
        for (AvailabilityTemplateSlot slot : slots) {
            serviceAvailabilityServicePort.create(new CreateServiceAvailabilityCommand(
                    serviceId,
                    (byte) slot.weekDay(),
                    slot.startTime(),
                    slot.endTime(),
                    true));
        }
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private ServiceSnapshot toServiceSnapshot(Service service) {
        return new ServiceSnapshot(
                service.getId(),
                service.getOffererId(),
                service.getTitle(),
                service.getDescription(),
                service.getPhotos(),
                service.getPriceHourly(),
                service.getCategoryId(),
                service.getAverageDurationMinutes(),
                Boolean.TRUE.equals(service.getActive()),
                service.getOperationRadiusKm(),
                service.getCreatedAt(),
                service.getUpdatedAt(),
                service.getDeletedAt());
    }

    private CategorySnapshot toCategorySnapshot(Category category) {
        return new CategorySnapshot(category.getId(), category.getName());
    }

    private ServiceAvailabilitySnapshot toAvailabilitySnapshot(ServiceAvailability availability) {
        return new ServiceAvailabilitySnapshot(
                availability.getId(),
                availability.getServiceId(),
                availability.getWeekDay(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.isActive());
    }
}
