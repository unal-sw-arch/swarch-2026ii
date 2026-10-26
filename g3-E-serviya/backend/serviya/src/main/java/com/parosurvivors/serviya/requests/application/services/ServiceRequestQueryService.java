package com.parosurvivors.serviya.requests.application.services;

import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.requests.application.dto.item.RequestHistoryItem;
import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.dto.result.AdminRequestDetailResult;
import com.parosurvivors.serviya.requests.application.dto.result.ServiceRequestDetailResult;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de lecturas de solicitudes (CQRS, módulo 4). Los listados paginados y filtrados van por el
 * {@link ServiceRequestReadPort} (query nativa enriquecida). El detalle NO usa query nativa: se
 * compone inyectando los puertos de servicio, categoría, perfiles y dirección, porque la dirección
 * (address_line) está cifrada y solo {@link AddressPersistencePort} la devuelve descifrada.
 * Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class ServiceRequestQueryService implements ServiceRequestQueryServicePort {

    private final ServiceRequestReadPort serviceRequestReadPort;
    private final ServicePersistencePort servicePersistencePort;
    private final CategoryPersistencePort categoryPersistencePort;
    private final UserProfilePersistencePort userProfilePersistencePort;
    private final AddressPersistencePort addressPersistencePort;

    /** Estados "activos" (no terminales) de una solicitud: aún en curso desde la óptica del cliente. */
    private static final List<RequestStatus> ACTIVE_STATUSES =
            List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED, RequestStatus.PRESUMABLY_COMPLETED);

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceRequestSummaryItem> getClientRequests(SearchServiceRequestsQuery query, Pageable pageable) {
        return serviceRequestReadPort.searchByClient(query, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveClientRequests(Long clientId) {
        return serviceRequestReadPort.countByClientIdAndStatusIn(clientId, ACTIVE_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceRequestSummaryItem> getOffererRequests(SearchServiceRequestsQuery query, Pageable pageable) {
        return serviceRequestReadPort.searchByOfferer(query, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceRequest> getClientFutureRequests(Long clientId, Pageable pageable) {
        return serviceRequestReadPort.findClientFutureRequests(clientId, List.of("ACCEPTED", "PENDING"), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceRequest> getOffererFutureRequests(Long offererId, Pageable pageable) {
        return serviceRequestReadPort.findOffererFutureRequests(offererId, List.of("ACCEPTED"), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceRequestDetailResult getRequestDetailForParty(Long requestId, Long requesterId) {
        ServiceRequest request = loadRequest(requestId);
        // Solo cliente u oferente ven el detalle; para el resto se oculta la existencia (403).
        if (!isParticipant(request, requesterId)) {
            throw new UnauthorizedException("El usuario no participa en la solicitud");
        }
        Enrichment e = enrich(request);
        // Solo la contraparte: si consulta el cliente, la contraparte es el oferente, y viceversa.
        boolean viewerIsClient = requesterId.equals(request.getClientId());
        Long counterpartyId = viewerIsClient ? request.getOffererId() : request.getClientId();
        String counterpartyName = viewerIsClient ? e.offererName() : e.clientName();
        String counterpartyPhotoUrl = viewerIsClient ? e.offererPhotoUrl() : e.clientPhotoUrl();
        return new ServiceRequestDetailResult(
                request.getId(), request.getStatus(), request.getScheduledDate(), request.getRequestedPrice(),
                request.getCreatedAt(), request.getCompletedAt(), request.getUpdatedStatusAt(),
                request.getPreviousRequestId(),
                request.getServiceId(), e.serviceTitle(), e.categoryName(), e.priceHourly(), e.averageDurationMinutes(),
                counterpartyId, counterpartyName, counterpartyPhotoUrl,
                request.getAddressId(), e.addressLine(), e.city(), e.latitude(), e.longitude());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRequestDetailResult getRequestDetailForAdmin(Long requestId) {
        // Vista administrativa (ambas partes). El gate de rol ADMIN vive en el endpoint (Spring Security),
        // no aqui: es una restriccion de rol pura, no una verificacion de propiedad/participacion.
        ServiceRequest request = loadRequest(requestId);
        Enrichment e = enrich(request);
        return new AdminRequestDetailResult(
                request.getId(), request.getStatus(), request.getScheduledDate(), request.getRequestedPrice(),
                request.getCreatedAt(), request.getCompletedAt(), request.getUpdatedStatusAt(),
                request.getUpdatedBy(), request.getPreviousRequestId(),
                request.getServiceId(), e.serviceTitle(), e.categoryName(), e.priceHourly(), e.averageDurationMinutes(),
                request.getClientId(), e.clientName(), e.clientPhotoUrl(),
                request.getOffererId(), e.offererName(), e.offererPhotoUrl(),
                request.getAddressId(), e.addressLine(), e.city(), e.latitude(), e.longitude());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestHistoryItem> getRequestHistory(Long requestId, Long requesterId, boolean isAdmin) {
        ServiceRequest request = loadRequest(requestId);
        // Solo el cliente/oferente participante o un admin pueden ver el historial (el admin hace bypass).
        if (!isAdmin && !isParticipant(request, requesterId)) {
            throw new UnauthorizedException("El usuario no participa en la solicitud");
        }
        // Retrocede hasta la raíz por previousRequestId y luego recorre la cadena hacia adelante,
        // de modo que el historial quede en orden cronológico (original -> reprogramaciones sucesivas).
        ServiceRequest root = request;
        while (root.getPreviousRequestId() != null) {
            Optional<ServiceRequest> previous = serviceRequestReadPort.findById(root.getPreviousRequestId());
            if (previous.isEmpty()) {
                break;
            }
            root = previous.get();
        }
        List<RequestHistoryItem> history = new ArrayList<>();
        ServiceRequest current = root;
        while (current != null) {
            history.add(new RequestHistoryItem(
                    current.getId(), current.getPreviousRequestId(), current.getStatus(),
                    current.getScheduledDate(), current.getUpdatedBy(), current.getUpdatedStatusAt()));
            current = serviceRequestReadPort.findByPreviousRequestId(current.getId()).orElse(null);
        }
        return history;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private ServiceRequest loadRequest(Long requestId) {
        return serviceRequestReadPort.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + requestId));
    }

    /** El usuario es parte (cliente u oferente) de la solicitud. */
    private boolean isParticipant(ServiceRequest request, Long userId) {
        return userId != null
                && (userId.equals(request.getClientId()) || userId.equals(request.getOffererId()));
    }

    /**
     * Reúne los datos enriquecidos del detalle desde los puertos de otros módulos. Cualquier relación
     * ausente deja su campo en null (no es un 404): el 404/403 solo lo decide la solicitud y la parte.
     */
    private Enrichment enrich(ServiceRequest request) {
        Optional<Service> service = servicePersistencePort.findById(request.getServiceId());
        String categoryName = service.map(Service::getCategoryId)
                .flatMap(categoryPersistencePort::findById)
                .map(c -> c.getName())
                .orElse(null);
        UserProfile client = userProfilePersistencePort.findByUserId(request.getClientId()).orElse(null);
        UserProfile offerer = userProfilePersistencePort.findByUserId(request.getOffererId()).orElse(null);
        Address address = request.getAddressId() == null ? null
                : addressPersistencePort.findById(request.getAddressId()).orElse(null);
        return new Enrichment(
                service.map(Service::getTitle).orElse(null),
                categoryName,
                service.map(Service::getPriceHourly).orElse(null),
                service.map(Service::getAverageDurationMinutes).orElse(null),
                client == null ? null : client.getFullName(),
                client == null ? null : client.getProfilePhotoUrl(),
                offerer == null ? null : offerer.getFullName(),
                offerer == null ? null : offerer.getProfilePhotoUrl(),
                address == null ? null : address.getAddressLine(),
                address == null ? null : address.getCity(),
                address == null ? null : address.getLatitude(),
                address == null ? null : address.getLongitude());
    }

    /** Datos enriquecidos comunes al detalle de parte y de admin. */
    private record Enrichment(
            String serviceTitle,
            String categoryName,
            java.math.BigDecimal priceHourly,
            Integer averageDurationMinutes,
            String clientName,
            String clientPhotoUrl,
            String offererName,
            String offererPhotoUrl,
            String addressLine,
            String city,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude) {
    }
}
