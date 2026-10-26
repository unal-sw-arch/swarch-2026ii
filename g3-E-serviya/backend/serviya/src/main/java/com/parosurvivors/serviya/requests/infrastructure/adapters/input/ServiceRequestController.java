package com.parosurvivors.serviya.requests.infrastructure.adapters.input;

import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.requests.infrastructure.adapters.input.api.ServiceRequestApi;
import com.parosurvivors.serviya.requests.infrastructure.dto.form.CreateServiceRequestForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.form.RescheduleRequestForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RequestHistoryResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestSummaryResponse;
import com.parosurvivors.serviya.requests.infrastructure.mappers.ServiceRequestWebMapper;
import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Adaptador de entrada (REST) de solicitudes de servicio. Placeholder funcional;
 * documentacion en {@link ServiceRequestApi}. Mapea Form->Command y dominio/Result/Item->Response.
 */
@RestController
@RequiredArgsConstructor
public class ServiceRequestController implements ServiceRequestApi {

    private final ServiceRequestQueryServicePort queryService;
    private final ServiceRequestCommandServicePort commandService;
    private final ServiceRequestWebMapper mapper;

    @Override
    @GetMapping("/api/v1/users/me/client-requests")
    public ResponseEntity<Page<ServiceRequestSummaryResponse>> getClientRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long counterpartyId,
            @RequestParam(required = false) String titleQuery,
            @RequestParam(required = false) LocalDateTime scheduledFrom,
            @RequestParam(required = false) LocalDateTime scheduledTo,
            @RequestParam(required = false) LocalDateTime createdFrom,
            @RequestParam(required = false) LocalDateTime createdTo,
            Pageable pageable) {
        SearchServiceRequestsQuery query = mapper.toQuery(CurrentUser.id(), statuses, serviceId, categoryId,
                counterpartyId, titleQuery, scheduledFrom, scheduledTo, createdFrom, createdTo);
        return ResponseEntity.ok(queryService.getClientRequests(query, pageable).map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/users/me/offerer-requests")
    public ResponseEntity<Page<ServiceRequestSummaryResponse>> getOffererRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long counterpartyId,
            @RequestParam(required = false) String titleQuery,
            @RequestParam(required = false) LocalDateTime scheduledFrom,
            @RequestParam(required = false) LocalDateTime scheduledTo,
            @RequestParam(required = false) LocalDateTime createdFrom,
            @RequestParam(required = false) LocalDateTime createdTo,
            Pageable pageable) {
        SearchServiceRequestsQuery query = mapper.toQuery(CurrentUser.id(), statuses, serviceId, categoryId,
                counterpartyId, titleQuery, scheduledFrom, scheduledTo, createdFrom, createdTo);
        return ResponseEntity.ok(queryService.getOffererRequests(query, pageable).map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/service-requests/{id}")
    public ResponseEntity<ServiceRequestDetailResponse> getRequestDetail(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(queryService.getRequestDetailForParty(id, CurrentUser.id())));
    }

    @Override
    @GetMapping("/api/v1/service-requests/{id}/history")
    public ResponseEntity<List<RequestHistoryResponse>> getRequestHistory(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toHistoryResponses(
                queryService.getRequestHistory(id, CurrentUser.id(), CurrentUser.isAdmin())));
    }

    @Override
    @PostMapping("/api/v1/service-requests")
    @RateLimit("request-write")
    public ResponseEntity<ServiceRequestResponse> createRequest(@Valid @RequestBody CreateServiceRequestForm form) {
        ServiceRequestResponse response = mapper.toResponse(
                commandService.createRequest(mapper.toCommand(form, CurrentUser.id())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PostMapping("/api/v1/service-requests/{id}/accept")
    @RateLimit("request-write")
    public ResponseEntity<Void> acceptRequest(@PathVariable Long id) {
        commandService.acceptRequest(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/service-requests/{id}/reject")
    @RateLimit("request-write")
    public ResponseEntity<Void> rejectRequest(@PathVariable Long id) {
        commandService.rejectRequest(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/service-requests/{id}/cancel")
    @RateLimit("request-write")
    public ResponseEntity<Void> cancelRequest(@PathVariable Long id) {
        commandService.cancelRequest(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/service-requests/{id}/mark-completed")
    @RateLimit("request-write")
    public ResponseEntity<Void> markCompleted(@PathVariable Long id) {
        commandService.markAsPresumablyCompleted(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/service-requests/{id}/confirm-completion")
    @RateLimit("request-write")
    public ResponseEntity<Void> confirmCompletion(@PathVariable Long id) {
        commandService.confirmCompletion(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/service-requests/{id}/reschedule")
    @RateLimit("request-write")
    public ResponseEntity<ServiceRequestResponse> rescheduleRequest(@PathVariable Long id,
                                                                    @Valid @RequestBody RescheduleRequestForm form) {
        ServiceRequestResponse response = mapper.toResponse(
                commandService.rescheduleRequest(id, form.newDate(), CurrentUser.id()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    

    @Override
    @GetMapping("/api/v1/users/me/client-agenda")
    public ResponseEntity<Page<ServiceRequestResponse>> getClientAgenda(Pageable pageable) {
        return ResponseEntity.ok(queryService.getClientFutureRequests(CurrentUser.id(), pageable)
                .map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/users/me/offerer-agenda")
    public ResponseEntity<Page<ServiceRequestResponse>> getOffererAgenda(Pageable pageable) {
        return ResponseEntity.ok(queryService.getOffererFutureRequests(CurrentUser.id(), pageable)
                .map(mapper::toResponse));
    }
}
