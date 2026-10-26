package com.parosurvivors.serviya.services.application.services;

import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceMetricsServicePort;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.profiles.application.ports.input.OffererProfileServicePort;
import com.parosurvivors.serviya.profiles.application.ports.input.UserProfileServicePort;
import com.parosurvivors.serviya.profiles.domain.OffererProfile;
import com.parosurvivors.serviya.profiles.domain.OffererProfileSummary;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.services.application.dto.command.CreateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.application.mappers.ServiceCommandMapper;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.services.application.ports.input.ServiceAvailabilityServicePort;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceCategoryPort;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.domain.ServiceDetail;
import com.parosurvivors.serviya.services.domain.FeedbackUser;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementacion del marketplace de servicios. Unico servicio del esqueleto con
 * logica real.
 * Construye el dominio a partir de los Commands (sin mapper web) y devuelve la
 * entidad de dominio;
 * el controller mapea a Response via ServiceWebMapper.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceService implements MarketplaceServicePort {

    private final ServicePersistencePort persistencePort;
    private final MarketplaceCategoryPort categoryPort;
    private final OffererProfileServicePort offererProfileService;
    private final OffererMetricsServicePort offererMetricsService;
    private final ServiceMetricsServicePort serviceMetricsService;
    private final ServiceFeedbackServicePort serviceFeedbackService;
    private final UserProfileServicePort userProfileService;
    private final ServiceAvailabilityServicePort serviceAvailabilityService;
    private final ServiceCommandMapper commandMapper;

    @Override
    public Service create(CreateServiceCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Service service = commandMapper.toDomain(command);
        service.setCreatedAt(now);
        service.setUpdatedAt(now);
        return persistencePort.save(service);
    }

    @Override
    public Optional<Service> getById(Long id) {
        return persistencePort.findById(id)
                .filter(s -> !s.isDeleted());
    }

    @Override
    public List<Service> getAll() {
        return persistencePort.findAll().stream()
                .filter(s -> !s.isDeleted())
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceDetail> getByOffererId(Long offererId) {
        List<Service> services = persistencePort.findByOffererId(offererId);

        if (services.isEmpty())
            throw new ResourceNotFoundException("El oferente no tiene servicios");

        List<ServiceDetail> details = new ArrayList<>();

        for (Service service : services) {
            Category category = categoryPort.getById(service.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Categoria no encontrada con id: " + service.getCategoryId()));
            details.add(ServiceDetail.builder()
                    .service(service)
                    .category(category)
                    .build());
        }

        return details;
    }

    @Override
    public Page<Service> search(SearchServiceQuery criteria, Pageable pageable) {
        return persistencePort.search(criteria, pageable);
    }

    @Override
    public Map<Long, ServiceMetrics> getMetricsForServices(List<Long> serviceIds) {
        return serviceMetricsService.getMetricsByServiceIds(serviceIds);
    }

    @Override
    public Service update(UpdateServiceCommand command, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(command.serviceId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Servicio no encontrado con id: " + command.serviceId()));

        requireOwnership(service, requesterId, isAdmin);

        // PATCH semantico: el mapper aplica solo los campos no-nulos del command
        // (IGNORE strategy).
        commandMapper.updateFromCommand(command, service);
        service.setUpdatedAt(LocalDateTime.now());

        return persistencePort.update(service);
    }

    @Override
    public Optional<ServiceDetail> getDetailById(Long id) {

        Service service = getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));

        Category category = categoryPort.getById(service.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoria no encontrada con id: " + service.getCategoryId()));

        OffererProfile offererProfile = offererProfileService.getPublicProfile(service.getOffererId());
        OffererProfileSummary summary = offererProfileService.getProfileSummary(service.getOffererId());
        OffererMetrics offererMetrics = offererMetricsService.getMainMetrics(service.getOffererId());
        ServiceMetrics serviceMetrics = serviceMetricsService.getMetrics(service.getId());

        // Reseñas recientes (hasta 3): cada feedback con comentario se empareja con el
        // perfil
        // público de su autor para mostrar nombre y foto en el detalle del servicio.
        List<FeedbackUser> feedbacks = serviceFeedbackService.getRecentServiceFeedback(service.getId(), 3).stream()
                .map(feedback -> new FeedbackUser(feedback, userProfileService.getProfileInfo(feedback.getClientId())))
                .collect(Collectors.toList());

        List<ServiceAvailability> availability = serviceAvailabilityService.getByServiceId(service.getId());

        return Optional.of(ServiceDetail.builder()
                .service(service)
                .category(category)
                .offererProfile(offererProfile)
                .offererSummary(summary)
                .feedbackUsers(feedbacks)
                .availability(availability)
                .serviceMetrics(serviceMetrics)
                .offererMetrics(offererMetrics)
                .build());
    }

    @Override
    public void delete(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));
        requireOwnership(service, requesterId, isAdmin);
        persistencePort.deleteById(id);
    }

    @Override
    public void softDelete(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));

        requireOwnership(service, requesterId, isAdmin);
        service.softDelete();
        service.setUpdatedAt(LocalDateTime.now());
        persistencePort.update(service);
    }

    @Override
    public void activate(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));

        requireOwnership(service, requesterId, isAdmin);
        service.activate();
        service.setUpdatedAt(LocalDateTime.now());
        persistencePort.update(service);
    }

    @Override
    public void deactivate(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));

        requireOwnership(service, requesterId, isAdmin);
        service.deactivate();
        service.setUpdatedAt(LocalDateTime.now());
        persistencePort.update(service);
    }

    /**
     * El actor debe ser el oferente propietario del servicio, o un admin (IDOR: B1).
     * Mismo patron "participante O admin" que {@code ServiceRequestQueryService.getRequestHistory}.
     */
    private void requireOwnership(Service service, Long requesterId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (requesterId == null || !requesterId.equals(service.getOffererId())) {
            throw new UnauthorizedException("El usuario no es el propietario del servicio");
        }
    }

    @Override
    public void deactivateAllByOfferer(Long offererId) {
        // Usado por UserDeletionService al eliminar un oferente: oculta todos sus servicios del buscador.
        // Corre dentro de la transacción del orquestador llamante.
        LocalDateTime now = LocalDateTime.now();
        for (Service service : persistencePort.findByOffererId(offererId)) {
            if (service.isAvailable()) {
                service.deactivate();
                service.setUpdatedAt(now);
                persistencePort.update(service);
            }
        }
    }

}
