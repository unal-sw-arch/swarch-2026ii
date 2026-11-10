package com.parosurvivors.serviya.requests.application.api;

import com.parosurvivors.serviya.requests.api.RequestCommandApi;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import com.parosurvivors.serviya.requests.api.dto.RequestDetailSnapshot;
import com.parosurvivors.serviya.requests.api.dto.RequestSearchCriteria;
import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import com.parosurvivors.serviya.requests.api.dto.RequestSummary;
import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementacion de los contratos publicos de requests sobre los casos de uso existentes.
 *
 * <p>Las lecturas simples usan {@link ServiceRequestReadPort} y devuelven datos propios de la
 * solicitud. Las vistas enriquecidas (nombres, servicio, categoria, direccion) se componen fuera
 * del modulo con las APIs de catalog.</p>
 */
@Component
@RequiredArgsConstructor
public class RequestApiFacade implements RequestQueryApi, RequestCommandApi {

    private final ServiceRequestQueryServicePort serviceRequestQueryServicePort;
    private final ServiceRequestCommandServicePort serviceRequestCommandServicePort;
    private final ServiceRequestReadPort serviceRequestReadPort;

    // ---------------------------------------------------------------------
    // RequestQueryApi
    // ---------------------------------------------------------------------

    @Override
    public Optional<RequestSnapshot> findById(Long requestId) {
        return serviceRequestReadPort.findById(requestId).map(RequestSnapshots::toSnapshot);
    }

    @Override
    public Optional<RequestDetailSnapshot> findDetail(Long requestId) {
        return serviceRequestReadPort.findById(requestId).map(this::toDetail);
    }

    @Override
    public long countActiveClientRequests(Long clientId) {
        return serviceRequestQueryServicePort.countActiveClientRequests(clientId);
    }

    @Override
    public long countByStatus(String status) {
        return serviceRequestReadPort.countByStatus(RequestStatus.valueOf(status));
    }

    @Override
    public PageResult<RequestSummary> searchForClient(
            Long clientId, RequestSearchCriteria criteria, int page, int size) {
        Page<ServiceRequestSummaryItem> result = serviceRequestQueryServicePort
                .getClientRequests(toQuery(clientId, criteria), PageRequest.of(page, size));
        return toPage(result);
    }

    @Override
    public PageResult<RequestSummary> searchForOfferer(
            Long offererId, RequestSearchCriteria criteria, int page, int size) {
        Page<ServiceRequestSummaryItem> result = serviceRequestQueryServicePort
                .getOffererRequests(toQuery(offererId, criteria), PageRequest.of(page, size));
        return toPage(result);
    }

    // ---------------------------------------------------------------------
    // RequestCommandApi
    // ---------------------------------------------------------------------

    @Override
    public List<RequestSnapshot> cancelActiveRequestsForUser(Long userId) {
        return serviceRequestCommandServicePort.cancelActiveRequestsForUser(userId).stream()
                .map(RequestSnapshots::toSnapshot)
                .toList();
    }

    @Override
    public List<RequestSnapshot> cancelActiveRequestsForRole(Long userId, boolean asOfferer) {
        return serviceRequestCommandServicePort.cancelActiveRequestsForRole(userId, asOfferer).stream()
                .map(RequestSnapshots::toSnapshot)
                .toList();
    }

    @Override
    public void markAsNotProvided(Long requestId, Long actorId) {
        serviceRequestCommandServicePort.markAsNotProvided(requestId, actorId);
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private SearchServiceRequestsQuery toQuery(Long viewerId, RequestSearchCriteria criteria) {
        if (criteria == null) {
            return new SearchServiceRequestsQuery(viewerId, List.of(), null, null, null, null,
                    null, null, null, null);
        }
        return new SearchServiceRequestsQuery(
                viewerId,
                criteria.statuses(),
                criteria.serviceId(),
                criteria.categoryId(),
                criteria.counterpartyId(),
                criteria.titleQuery(),
                criteria.scheduledFrom(),
                criteria.scheduledTo(),
                criteria.createdFrom(),
                criteria.createdTo());
    }

    private PageResult<RequestSummary> toPage(Page<ServiceRequestSummaryItem> page) {
        return new PageResult<>(
                page.getContent().stream().map(this::toSummary).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private RequestSummary toSummary(ServiceRequestSummaryItem item) {
        return new RequestSummary(
                item.requestId(),
                item.status(),
                item.scheduledDate(),
                item.requestedPrice(),
                item.previousRequestId(),
                item.createdAt(),
                item.serviceId(),
                item.counterpartyId());
    }

    private RequestDetailSnapshot toDetail(ServiceRequest request) {
        return new RequestDetailSnapshot(
                request.getId(),
                request.getStatus() == null ? null : request.getStatus().name(),
                request.getScheduledDate(),
                request.getRequestedPrice(),
                request.getCreatedAt(),
                request.getCompletedAt(),
                request.getUpdatedStatusAt(),
                request.getUpdatedBy(),
                request.getPreviousRequestId(),
                request.getServiceId(),
                request.getClientId(),
                request.getOffererId(),
                request.getAddressId());
    }
}
