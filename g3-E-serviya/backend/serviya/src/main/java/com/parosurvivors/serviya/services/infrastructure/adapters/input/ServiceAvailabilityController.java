package com.parosurvivors.serviya.services.infrastructure.adapters.input;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parosurvivors.serviya.services.application.ports.input.ServiceAvailabilityServicePort;
import com.parosurvivors.serviya.services.infrastructure.adapters.input.api.ServiceAvailabilityApi;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceAvailabilityForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceAvailabilityForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceAvailabilityResponse;
import com.parosurvivors.serviya.services.infrastructure.mappers.ServiceAvailabilityWebMapper;
import com.parosurvivors.serviya.shared.security.CurrentUser;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/service-availabilities")
@RequiredArgsConstructor
public class ServiceAvailabilityController implements ServiceAvailabilityApi{

    private final ServiceAvailabilityServicePort serviceAvailabilityService;
    private final ServiceAvailabilityWebMapper mapper;
    
    @Override
    @PostMapping("/service/{serviceId}")
    public ResponseEntity<ServiceAvailabilityResponse> create(@PathVariable Long serviceId, @Valid @RequestBody CreateServiceAvailabilityForm form) {
        ServiceAvailabilityResponse response = mapper.toResponse(
            serviceAvailabilityService.create(mapper.toCommand(form, serviceId))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        serviceAvailabilityService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<ServiceAvailabilityResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateServiceAvailabilityForm form) {
        ServiceAvailabilityResponse response = mapper.toResponse(
            serviceAvailabilityService.update(mapper.toCommand(form, id))
        );
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/service/{id}")
    public ResponseEntity<List<ServiceAvailabilityResponse>> getByServiceId(@Parameter(description = "ID del servicio")@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponses(serviceAvailabilityService.getByServiceId(id)));
    }

    @Override
    @PostMapping("/service/{serviceId}/apply-template")
    public ResponseEntity<Void> applyGeneralTemplate(@PathVariable Long serviceId) {
        serviceAvailabilityService.applyGeneralTemplate(serviceId, CurrentUser.id());
        return ResponseEntity.ok().build();
    }
}
