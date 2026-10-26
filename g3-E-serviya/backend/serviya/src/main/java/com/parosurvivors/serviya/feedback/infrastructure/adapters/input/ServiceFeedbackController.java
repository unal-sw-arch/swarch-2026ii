package com.parosurvivors.serviya.feedback.infrastructure.adapters.input;

import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackTagCatalogServicePort;
import com.parosurvivors.serviya.feedback.infrastructure.adapters.input.api.ServiceFeedbackApi;
import com.parosurvivors.serviya.feedback.infrastructure.dto.form.SubmitServiceFeedbackForm;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ServiceFeedbackResponse;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ServiceFeedbackTagResponse;
import com.parosurvivors.serviya.feedback.infrastructure.mappers.ServiceFeedbackWebMapper;
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
 * Adaptador de entrada (REST) del feedback de servicio. Placeholder funcional;
 * documentacion en {@link ServiceFeedbackApi}. Mapea Form->Command y Result/dominio->Response.
 */
@RestController
@RequiredArgsConstructor
public class ServiceFeedbackController implements ServiceFeedbackApi {

    private final ServiceFeedbackServicePort serviceFeedbackService;
    private final ServiceFeedbackTagCatalogServicePort serviceFeedbackTagCatalogService;
    private final ServiceFeedbackWebMapper mapper;

    @Override
    @PostMapping("/api/v1/service-requests/{id}/feedback")
    public ResponseEntity<Void> submitServiceFeedback(@PathVariable Long id,
                                                      @Valid @RequestBody SubmitServiceFeedbackForm form) {
        serviceFeedbackService.submitServiceFeedback(mapper.toCommand(form, currentUserId(), id));
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/api/v1/service-requests/{id}/feedback")
    public ResponseEntity<ServiceFeedbackResponse> getServiceFeedback(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(serviceFeedbackService.getServiceFeedback(id)));
    }

    @GetMapping("/api/v1/service-requests/{id}/feedback/exists")
    public ResponseEntity<Boolean> serviceFeedbackExists(@PathVariable Long id) {
        return ResponseEntity.ok(serviceFeedbackService.existsForRequest(id));
    }

    @Override
    @GetMapping("/api/v1/services/{id}/feedback")
    public ResponseEntity<Page<ServiceFeedbackResponse>> getServiceFeedbackList(@PathVariable Long id,
                                                                               Pageable pageable) {
        return ResponseEntity.ok(serviceFeedbackService.getServiceFeedbackList(id, pageable)
                .map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/clients/{id}/service-feedback")
    public ResponseEntity<Page<ServiceFeedbackResponse>> getServiceFeedbackByClient(@PathVariable Long id,
                                                                                   Pageable pageable) {
        return ResponseEntity.ok(serviceFeedbackService.getServiceFeedbackByClient(id, pageable)
                .map(mapper::toResponse));
    }

    @Override
    @GetMapping("/api/v1/service-feedback-tags")
    public ResponseEntity<List<ServiceFeedbackTagResponse>> getCatalog() {
        return ResponseEntity.ok(mapper.toTagResponses(serviceFeedbackTagCatalogService.getCatalog()));
    }

    private Long currentUserId() {
        return CurrentUser.id();
    }
}
