package com.parosurvivors.serviya.feedback.infrastructure.adapters.input;

import com.parosurvivors.serviya.feedback.application.ports.input.ClientFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.input.ClientFeedbackTagCatalogServicePort;
import com.parosurvivors.serviya.feedback.infrastructure.adapters.input.api.ClientFeedbackApi;
import com.parosurvivors.serviya.feedback.infrastructure.dto.form.SubmitClientFeedbackForm;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ClientFeedbackResponse;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ClientFeedbackTagResponse;
import com.parosurvivors.serviya.feedback.infrastructure.mappers.ClientFeedbackWebMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada (REST) del feedback de cliente. Placeholder funcional;
 * documentacion en {@link ClientFeedbackApi}. Mapea Form->Command y Result/dominio->Response.
 */
@RestController
@RequiredArgsConstructor
public class ClientFeedbackController implements ClientFeedbackApi {

    private final ClientFeedbackServicePort clientFeedbackService;
    private final ClientFeedbackTagCatalogServicePort clientFeedbackTagCatalogService;
    private final ClientFeedbackWebMapper mapper;

    @Override
    @PostMapping("/api/v1/service-requests/{id}/client-feedback")
    public ResponseEntity<Void> submitClientFeedback(@PathVariable Long id,
                                                     @Valid @RequestBody SubmitClientFeedbackForm form) {
        clientFeedbackService.submitClientFeedback(mapper.toCommand(form, currentUserId(), id));
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/api/v1/service-requests/{id}/client-feedback")
    public ResponseEntity<ClientFeedbackResponse> getClientFeedback(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(clientFeedbackService.getClientFeedback(id)));
    }

    @GetMapping("/api/v1/service-requests/{id}/client-feedback/exists")
    public ResponseEntity<Boolean> clientFeedbackExists(@PathVariable Long id) {
        return ResponseEntity.ok(clientFeedbackService.existsForRequest(id));
    }

    @Override
    @GetMapping("/api/v1/users/{id}/client-feedback")
    public ResponseEntity<Page<ClientFeedbackResponse>> getClientFeedbackList(@PathVariable Long id,
                                                                             Pageable pageable) {
        return ResponseEntity.ok(clientFeedbackService.getClientFeedbackList(id, pageable)
                .map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/offerers/{id}/client-feedback")
    public ResponseEntity<Page<ClientFeedbackResponse>> getClientFeedbackByOfferer(@PathVariable Long id,
                                                                                  Pageable pageable) {
        return ResponseEntity.ok(clientFeedbackService.getClientFeedbackByOfferer(id, pageable)
                .map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/client-feedback-tags")
    public ResponseEntity<List<ClientFeedbackTagResponse>> getCatalog() {
        return ResponseEntity.ok(mapper.toTagResponses(clientFeedbackTagCatalogService.getCatalog()));
    }

    private Long currentUserId() {
        return CurrentUser.id();
    }
}
