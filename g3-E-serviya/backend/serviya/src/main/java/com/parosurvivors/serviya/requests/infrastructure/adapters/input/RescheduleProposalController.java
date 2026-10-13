package com.parosurvivors.serviya.requests.infrastructure.adapters.input;

import com.parosurvivors.serviya.requests.application.ports.input.RescheduleProposalServicePort;
import com.parosurvivors.serviya.requests.infrastructure.adapters.input.api.RescheduleProposalApi;
import com.parosurvivors.serviya.requests.infrastructure.dto.form.CreateRescheduleProposalForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalSummaryResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestResponse;
import com.parosurvivors.serviya.requests.application.dto.query.SearchRescheduleProposalsQuery;
import com.parosurvivors.serviya.requests.infrastructure.mappers.RescheduleProposalWebMapper;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Adaptador de entrada (REST) de propuestas de reprogramacion. Placeholder funcional;
 * documentacion en {@link RescheduleProposalApi}. Mapea Form->Command y dominio->Response.
 */
@RestController
@RequiredArgsConstructor
public class RescheduleProposalController implements RescheduleProposalApi {

    private final RescheduleProposalServicePort proposalService;
    private final RescheduleProposalWebMapper mapper;
    private final ServiceRequestWebMapper serviceRequestMapper;

    @Override
    @PostMapping("/api/v1/reschedule-proposals")
    @RateLimit("proposal-write")
    public ResponseEntity<RescheduleProposalResponse> createProposal(
            @Valid @RequestBody CreateRescheduleProposalForm form) {
        RescheduleProposalResponse response = mapper.toResponse(
                proposalService.createProposal(mapper.toCommand(form, CurrentUser.id())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/api/v1/users/me/proposals/received")
    public ResponseEntity<Page<RescheduleProposalSummaryResponse>> getProposalsReceived(
            List<String> statuses, LocalDateTime proposedFrom, LocalDateTime proposedTo,
            LocalDateTime createdFrom, LocalDateTime createdTo, Long serviceId, Pageable pageable) {
        SearchRescheduleProposalsQuery query = mapper.toQuery(
                CurrentUser.id(), statuses, proposedFrom, proposedTo, createdFrom, createdTo, serviceId);
        return ResponseEntity.ok(proposalService.getProposalsForClient(query, pageable)
                .map(mapper::toSummaryResponse));
    }

    @Override
    @GetMapping("/api/v1/users/me/proposals/sent")
    public ResponseEntity<Page<RescheduleProposalSummaryResponse>> getProposalsSent(
            List<String> statuses, LocalDateTime proposedFrom, LocalDateTime proposedTo,
            LocalDateTime createdFrom, LocalDateTime createdTo, Long serviceId, Pageable pageable) {
        SearchRescheduleProposalsQuery query = mapper.toQuery(
                CurrentUser.id(), statuses, proposedFrom, proposedTo, createdFrom, createdTo, serviceId);
        return ResponseEntity.ok(proposalService.getProposalsByOfferer(query, pageable)
                .map(mapper::toSummaryResponse));
    }

    @Override
    @GetMapping("/api/v1/reschedule-proposals/{id}")
    public ResponseEntity<RescheduleProposalDetailResponse> getProposalDetail(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toDetailResponse(
                proposalService.getProposalDetail(id, CurrentUser.id())));
    }

    @Override
    @GetMapping("/api/v1/service-requests/{id}/proposals")
    public ResponseEntity<List<RescheduleProposalResponse>> getProposalsByRequest(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponses(proposalService.getProposalsByRequest(id, CurrentUser.id())));
    }

    @Override
    @PostMapping("/api/v1/reschedule-proposals/{id}/accept")
    @RateLimit("proposal-write")
    public ResponseEntity<ServiceRequestResponse> acceptProposal(@PathVariable Long id) {
        ServiceRequestResponse response = serviceRequestMapper.toResponse(
                proposalService.acceptProposal(id, CurrentUser.id()));
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/api/v1/reschedule-proposals/{id}/reject")
    @RateLimit("proposal-write")
    public ResponseEntity<Void> rejectProposal(@PathVariable Long id) {
        proposalService.rejectProposal(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/reschedule-proposals/{id}/cancel")
    @RateLimit("proposal-write")
    public ResponseEntity<Void> cancelProposal(@PathVariable Long id) {
        proposalService.cancelProposal(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
