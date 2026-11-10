package com.parosurvivors.serviya.bff.infrastructure.adapters.input;

import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.OffererAvailabilitySnapshot;
import com.parosurvivors.serviya.services.api.ServiceCommandApi;
import com.parosurvivors.serviya.services.api.dto.AvailabilityTemplateSlot;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Composicion de la plantilla de disponibilidad: el BFF lee la plantilla del oferente en profiles y
 * la aplica al servicio mediante services.api.
 */
@RestController
@RequiredArgsConstructor
public class ServiceAvailabilityTemplateController {

    private final ProfileQueryApi profileQueryApi;
    private final ServiceCommandApi serviceCommandApi;

    @PostMapping("/api/v1/service-availabilities/service/{serviceId}/apply-template")
    public ResponseEntity<Void> applyGeneralTemplate(@PathVariable Long serviceId) {
        List<AvailabilityTemplateSlot> slots = profileQueryApi
                .getAvailabilities(CurrentUser.id()).stream()
                .filter(OffererAvailabilitySnapshot::active)
                .map(slot -> new AvailabilityTemplateSlot(
                        slot.weekDay(), slot.startTime(), slot.endTime()))
                .toList();
        serviceCommandApi.applyAvailabilityTemplate(serviceId, slots);
        return ResponseEntity.ok().build();
    }
}
